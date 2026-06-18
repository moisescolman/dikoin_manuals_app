package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.servicios.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/export-jobs")
@RequiredArgsConstructor
public class ExportJobRestController {

    private final ExportService exportService;

    @PostMapping("/manuals/{manualId}/pdf")
    public ExportJobResponse exportPdf(@PathVariable Long manualId) {
        return exportService.exportPdf(manualId);
    }
}
