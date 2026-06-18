package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.importjob.ImportJobResponse;
import com.dikoin.manuals.entidades.*;
import com.dikoin.manuals.enums.*;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ImportJobMapper;
import com.dikoin.manuals.repositorios.*;
import com.dikoin.manuals.servicios.AssetStorageService;
import com.dikoin.manuals.servicios.ImportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final AssetStorageService assetStorageService;
    private final ProductRepository productRepository;
    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final ImportJobRepository importJobRepository;
    private final AssetRepository assetRepository;
    private final ImportJobMapper importJobMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ImportJobResponse importDocument(MultipartFile file, Long productId, String manualCode, String title, LanguageCode languageCode) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Archivo vacio");
        }

        String extension = extension(file.getOriginalFilename());
        if (!List.of("docx", "doc", "odt", "pdf").contains(extension)) {
            throw new ApiException("Formato no soportado: " + extension);
        }

        Path storedFile = assetStorageService.storeRawFile(file, "imports/" + extension);
        ImportJob job = ImportJob.builder()
                .sourceFilename(file.getOriginalFilename())
                .storedPath(storedFile.toString())
                .fileExtension(extension)
                .status(ImportStatus.PROCESSING)
                .languageCode(languageCode == null ? LanguageCode.ES : languageCode)
                .detectedSections(0)
                .detectedTables(0)
                .detectedImages(0)
                .build();
        job = importJobRepository.save(job);

        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + productId));

            Manual manual = manualRepository.findByCodeIgnoreCase(manualCode)
                    .orElseGet(() -> manualRepository.save(Manual.builder()
                            .code(manualCode)
                            .title(title)
                            .category("Importado")
                            .product(product)
                            .build()));

            manual.setTitle(title);
            manualRepository.save(manual);

            ParsedDocument parsed = parseDocument(storedFile, extension, manual);
            ManualVersion version = createImportedVersion(manual, parsed, job.getLanguageCode());
            job.setManualVersion(version);
            job.setDetectedSections(version.getSections().size());
            job.setDetectedTables(parsed.tableCount());
            job.setDetectedImages(parsed.imageCount());
            job.setStatus(ImportStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage("Importacion completada. Revise el borrador antes de publicar.");
            return importJobMapper.toResponse(importJobRepository.save(job));
        } catch (Exception ex) {
            job.setStatus(ImportStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage(ex.getMessage());
            importJobRepository.save(job);
            throw new ApiException("No se pudo importar el documento: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ImportJobResponse findById(Long id) {
        return importJobMapper.toResponse(importJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Importacion no encontrada: " + id)));
    }

    @Override
    public List<ImportJobResponse> findRecent() {
        return importJobRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(importJobMapper::toResponse)
                .toList();
    }

    private ParsedDocument parseDocument(Path path, String extension, Manual manual) throws Exception {
        String text = extractText(path);
        int tableCount = 0;
        int imageCount = 0;
        List<List<List<String>>> tables = new ArrayList<>();

        if ("docx".equals(extension)) {
            DocxExtras extras = extractDocxExtras(path, manual);
            tableCount = extras.tables().size();
            imageCount = extras.imageCount();
            tables.addAll(extras.tables());
        } else if ("pdf".equals(extension)) {
            imageCount = extractPdfImages(path, manual);
        } else if ("odt".equals(extension)) {
            imageCount = extractOdtImages(path, manual);
        }

        return new ParsedDocument(text, tables, tableCount, imageCount);
    }

    private String extractText(Path path) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    private DocxExtras extractDocxExtras(Path path, Manual manual) throws IOException {
        List<List<List<String>>> tables = new ArrayList<>();
        int images = 0;
        try (InputStream input = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(input)) {
            for (XWPFTable table : document.getTables()) {
                List<List<String>> rows = new ArrayList<>();
                for (XWPFTableRow row : table.getRows()) {
                    rows.add(row.getTableCells().stream().map(XWPFTableCell::getText).toList());
                }
                tables.add(rows);
            }
            for (XWPFPictureData picture : document.getAllPictures()) {
                saveExtractedBytes(picture.getData(), picture.getFileName(), picture.getPackagePart().getContentType(), manual);
                images++;
            }
        }
        return new DocxExtras(tables, images);
    }

    private int extractPdfImages(Path path, Manual manual) throws IOException {
        int count = 0;
        try (PDDocument document = PDDocument.load(path.toFile())) {
            int pageNumber = 1;
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    PDXObject xObject = page.getResources().getXObject(name);
                    if (xObject instanceof PDImageXObject image) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(image.getImage(), "png", out);
                        saveExtractedBytes(out.toByteArray(), "pdf_page_" + pageNumber + "_image_" + count + ".png", "image/png", manual);
                        count++;
                    }
                }
                pageNumber++;
            }
        }
        return count;
    }

    private int extractOdtImages(Path path, Manual manual) throws IOException {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().startsWith("Pictures/")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    String filename = Path.of(entry.getName()).getFileName().toString();
                    saveExtractedBytes(out.toByteArray(), filename, null, manual);
                    count++;
                }
            }
        }
        return count;
    }

    private void saveExtractedBytes(byte[] bytes, String originalFilename, String mimeType, Manual manual) throws IOException {
        String safeManual = manual.getCode().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path folder = assetStorageService.resolve("assets/extracted/" + safeManual);
        Files.createDirectories(folder);
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destination = folder.resolve(storedName);
        Files.write(destination, bytes);

        assetRepository.save(Asset.builder()
                .originalFilename(originalFilename)
                .storedFilename(storedName)
                .mimeType(mimeType)
                .fileSize((long) bytes.length)
                .storagePath("assets/extracted/" + safeManual + "/" + storedName)
                .assetType(AssetType.EXTRACTED_IMAGE)
                .manual(manual)
                .build());
    }

    private ManualVersion createImportedVersion(Manual manual, ParsedDocument parsed, LanguageCode languageCode) {
        manualVersionRepository.findByManualIdOrderByCreatedAtDesc(manual.getId())
                .forEach(version -> version.setActive(false));

        ManualVersion version = ManualVersion.builder()
                .manual(manual)
                .versionNumber("0.1-import")
                .status(ManualStatus.DRAFT)
                .active(true)
                .esReady(languageCode == LanguageCode.ES)
                .enReady(languageCode == LanguageCode.EN)
                .changeNotes("Borrador generado por importacion")
                .build();

        List<DetectedSection> sections = detectSections(parsed.text());
        int sort = 1;
        for (DetectedSection detected : sections) {
            ManualSection section = ManualSection.builder()
                    .manualVersion(version)
                    .sortOrder(sort)
                    .sectionNumber(String.valueOf(sort))
                    .titleEs(languageCode == LanguageCode.ES ? detected.title() : "Pendiente de traduccion")
                    .titleEn(languageCode == LanguageCode.EN ? detected.title() : null)
                    .completionStatus("IMPORTED")
                    .build();

            section.getBlocks().add(ManualBlock.builder()
                    .section(section)
                    .sortOrder(1)
                    .blockType(BlockType.PARAGRAPH)
                    .languageCode(languageCode)
                    .contentJson(json(Map.of("type", "paragraph", "text", detected.body())))
                    .build());

            version.getSections().add(section);
            sort++;
        }

        if (!parsed.tables().isEmpty()) {
            ManualSection tableSection = ManualSection.builder()
                    .manualVersion(version)
                    .sortOrder(sort)
                    .sectionNumber(String.valueOf(sort))
                    .titleEs("Tablas importadas")
                    .titleEn("Imported tables")
                    .completionStatus("IMPORTED")
                    .build();
            int blockSort = 1;
            for (List<List<String>> table : parsed.tables()) {
                tableSection.getBlocks().add(ManualBlock.builder()
                        .section(tableSection)
                        .sortOrder(blockSort++)
                        .blockType(BlockType.TABLE)
                        .languageCode(languageCode)
                        .contentJson(json(Map.of("type", "table", "rows", table)))
                        .build());
            }
            version.getSections().add(tableSection);
        }

        return manualVersionRepository.save(version);
    }

    private List<DetectedSection> detectSections(String text) {
        List<DetectedSection> sections = new ArrayList<>();
        String currentTitle = "Contenido importado";
        StringBuilder currentBody = new StringBuilder();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (isHeading(line)) {
                if (currentBody.length() > 0) {
                    sections.add(new DetectedSection(currentTitle, currentBody.toString().trim()));
                    currentBody.setLength(0);
                }
                currentTitle = line.replaceFirst("^\\d+(\\.\\d+)*\\s*", "");
            } else {
                currentBody.append(line).append("\n");
            }
        }

        if (currentBody.length() > 0) {
            sections.add(new DetectedSection(currentTitle, currentBody.toString().trim()));
        }

        if (sections.isEmpty()) {
            sections.add(new DetectedSection("Contenido importado", text == null ? "" : text.trim()));
        }
        return sections;
    }

    private boolean isHeading(String line) {
        return line.matches("^\\d+(\\.\\d+)*\\s+.{3,120}$")
                || line.matches("(?i)^(introduccion|objetivo|material|descripcion|procedimiento|resultados|conclusiones|seguridad).*")
                || (line.length() < 80 && line.equals(line.toUpperCase(Locale.ROOT)) && line.matches(".*[A-Z].*"));
    }

    private String json(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException ex) {
            throw new ApiException("No se pudo generar JSON de contenido", ex);
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private record ParsedDocument(String text, List<List<List<String>>> tables, int tableCount, int imageCount) {
    }

    private record DocxExtras(List<List<List<String>>> tables, int imageCount) {
    }

    private record DetectedSection(String title, String body) {
    }
}
