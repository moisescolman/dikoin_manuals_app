package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;

public interface ExportService {
    ExportJobResponse exportPdf(Long manualId);
}
