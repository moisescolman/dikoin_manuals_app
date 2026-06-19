package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.config.StorageProperties;
import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.entidades.ExportJob;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.entidades.ManualVersion;
import com.dikoin.manuals.enums.ExportStatus;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ExportJobMapper;
import com.dikoin.manuals.repositorios.ExportJobRepository;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.repositorios.ManualVersionRepository;
import com.dikoin.manuals.servicios.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportJobMapper exportJobMapper;
    private final StorageProperties storageProperties;

    @Value("${manuals.export.frontend-print-base-url:http://localhost:5173}")
    private String frontendPrintBaseUrl;

    @Value("${manuals.export.chrome-path:}")
    private String configuredChromePath;

    @Override
    @Transactional
    public ExportJobResponse exportPdf(Long manualId, LanguageCode languageCode) {
        Manual manual = manualRepository.findById(manualId)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + manualId));
        ManualVersion version = manualVersionRepository.findByManualIdAndActiveTrue(manualId)
                .orElseThrow(() -> new ApiException("El manual no tiene version activa"));

        ExportJob job = exportJobRepository.save(ExportJob.builder()
                .manualVersion(version)
                .status(ExportStatus.PROCESSING)
                .build());

        try {
            Path basePath = Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize();
            Path folder = basePath.resolve("exports/pdf");
            Files.createDirectories(folder);
            Path pdfPath = folder.resolve(pdfFilename(manual, version, languageCode));

            renderWithChrome(manual.getId(), languageCode, pdfPath);

            job.setStatus(ExportStatus.COMPLETED);
            job.setPdfPath(basePath.relativize(pdfPath.toAbsolutePath().normalize()).toString().replace("\\", "/"));
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage("PDF generado desde la vista previa HTML.");
            return exportJobMapper.toResponse(exportJobRepository.save(job));
        } catch (Exception ex) {
            job.setStatus(ExportStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLogMessage(ex.getMessage());
            exportJobRepository.save(job);
            throw new ApiException("No se pudo exportar PDF: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Path resolvePdfPath(Long exportJobId) {
        ExportJob job = exportJobRepository.findById(exportJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Exportacion no encontrada: " + exportJobId));
        if (job.getStatus() != ExportStatus.COMPLETED || job.getPdfPath() == null || job.getPdfPath().isBlank()) {
            throw new ApiException("La exportacion no tiene un PDF disponible");
        }

        Path basePath = Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize();
        Path pdfPath = basePath.resolve(job.getPdfPath()).normalize();
        if (!pdfPath.startsWith(basePath) || !Files.exists(pdfPath)) {
            throw new ResourceNotFoundException("Archivo PDF no encontrado");
        }
        return pdfPath;
    }

    private void renderWithChrome(Long manualId, LanguageCode languageCode, Path pdfPath) throws IOException, InterruptedException {
        String chromePath = resolveChromePath();
        Path userDataDir = Files.createTempDirectory("dikoin-pdf-chrome-");
        try {
            List<String> command = new ArrayList<>();
            command.add(chromePath);
            command.add("--headless=new");
            command.add("--disable-gpu");
            command.add("--no-sandbox");
            command.add("--disable-dev-shm-usage");
            command.add("--run-all-compositor-stages-before-draw");
            command.add("--virtual-time-budget=30000");
            command.add("--user-data-dir=" + userDataDir);
            command.add("--print-to-pdf=" + pdfPath.toAbsolutePath());
            command.add("--print-to-pdf-no-header");
            command.add(printUrl(manualId, languageCode));

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(75, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException("Chrome no termino la exportacion PDF a tiempo");
            }
            if (process.exitValue() != 0) {
                throw new ApiException("Chrome no pudo generar el PDF: " + output);
            }
            if (!Files.exists(pdfPath) || Files.size(pdfPath) == 0) {
                throw new ApiException("Chrome no genero un archivo PDF valido: " + output);
            }
        } finally {
            deleteQuietly(userDataDir);
        }
    }

    private String printUrl(Long manualId, LanguageCode languageCode) {
        String baseUrl = frontendPrintBaseUrl.replaceAll("/$", "");
        return baseUrl + "/manuales/" + manualId + "/print?lang=" + languageCode;
    }

    private String resolveChromePath() {
        if (configuredChromePath != null && !configuredChromePath.isBlank() && Files.exists(Path.of(configuredChromePath))) {
            return configuredChromePath;
        }

        List<String> candidates = List.of(
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        );

        return candidates.stream()
                .filter(candidate -> Files.exists(Path.of(candidate)))
                .findFirst()
                .orElseThrow(() -> new ApiException("No se encontro Chrome/Edge para exportar el PDF. Configure manuals.export.chrome-path."));
    }

    private String pdfFilename(Manual manual, ManualVersion version, LanguageCode languageCode) {
        return sanitizeFilename(manual.getCode()) + "_v" + sanitizeFilename(version.getVersionNumber()) + "_" + languageCode + ".pdf";
    }

    private String sanitizeFilename(String value) {
        return (value == null ? "manual" : value).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void deleteQuietly(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
