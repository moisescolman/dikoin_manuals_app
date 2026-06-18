package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.config.StorageProperties;
import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.entidades.ExportJob;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualVersion;
import com.dikoin.manuals.enums.ExportStatus;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ExportJobMapper;
import com.dikoin.manuals.repositorios.ExportJobRepository;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.repositorios.ManualVersionRepository;
import com.dikoin.manuals.servicios.ExportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportJobMapper exportJobMapper;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ExportJobResponse exportPdf(Long manualId) {
        Manual manual = manualRepository.findById(manualId)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + manualId));
        ManualVersion version = manualVersionRepository.findByManualIdAndActiveTrue(manualId)
                .orElseThrow(() -> new ApiException("El manual no tiene version activa"));

        ExportJob job = exportJobRepository.save(ExportJob.builder()
                .manualVersion(version)
                .status(ExportStatus.PROCESSING)
                .build());

        try {
            Path folder = Path.of(storageProperties.getBasePath()).resolve("exports/pdf");
            Files.createDirectories(folder);
            Path pdfPath = folder.resolve(manual.getCode().replaceAll("[^a-zA-Z0-9._-]", "_") + "_v" + version.getVersionNumber() + ".pdf");
            createSimplePdf(manual, version, pdfPath);
            job.setStatus(ExportStatus.COMPLETED);
            job.setPdfPath(Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize()
                    .relativize(pdfPath.toAbsolutePath().normalize()).toString().replace("\\", "/"));
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage("PDF generado correctamente. Maquetacion corporativa avanzada pendiente de fase visual.");
            return exportJobMapper.toResponse(exportJobRepository.save(job));
        } catch (Exception ex) {
            job.setStatus(ExportStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage(ex.getMessage());
            exportJobRepository.save(job);
            throw new ApiException("No se pudo exportar PDF: " + ex.getMessage(), ex);
        }
    }

    private void createSimplePdf(Manual manual, ManualVersion version, Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font regular = PDType1Font.HELVETICA;
            PDType1Font bold = PDType1Font.HELVETICA_BOLD;
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 780;
                content.beginText();
                content.setFont(bold, 16);
                content.newLineAtOffset(50, y);
                content.showText(manual.getCode() + " - " + safe(manual.getTitle()));
                content.endText();

                y -= 32;
                for (var section : version.getSections()) {
                    y = writeLine(content, bold, 12, 50, y, safe(section.getSectionNumber()) + " " + safe(section.getTitleEs()));
                    y -= 8;
                    for (ManualBlock block : section.getBlocks()) {
                        String text = extractReadableText(block.getContentJson());
                        for (String line : wrap(text, 95).split("\\R")) {
                            if (y < 70) {
                                break;
                            }
                            y = writeLine(content, regular, 9, 60, y, line);
                        }
                        y -= 8;
                    }
                    y -= 8;
                    if (y < 70) {
                        y = writeLine(content, regular, 9, 50, y, "Contenido continua en version web.");
                        break;
                    }
                }
            }
            document.save(pdfPath.toFile());
        }
    }

    private float writeLine(PDPageContentStream content, PDType1Font font, int size, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
        return y - (size + 5);
    }

    private String extractReadableText(String contentJson) {
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            if (node.has("text")) {
                return node.get("text").asText();
            }
            if (node.has("rows")) {
                return "Tabla: " + node.get("rows").size() + " filas";
            }
            return node.toString();
        } catch (Exception ex) {
            return contentJson;
        }
    }

    private String wrap(String text, int length) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(.{1," + length + "})(\\s+|$)", "$1\n");
    }

    private String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n\\t]", " ")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("ñ", "n")
                .replace("Ñ", "N")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
