package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.servicios.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/export-jobs")
@RequiredArgsConstructor
public class ExportJobRestController {

    private final ExportService exportService;

    @PostMapping("/manuals/{manualId}/pdf")
    public ExportJobResponse exportPdf(
            @PathVariable Long manualId,
            @RequestParam(defaultValue = "ES") LanguageCode lang
    ) {
        return exportService.exportPdf(manualId, lang);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) throws MalformedURLException {
        Path path = exportService.resolvePdfPath(id);
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
}
