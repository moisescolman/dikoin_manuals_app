package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.enums.LanguageCode;

import java.nio.file.Path;

public interface ExportService {
    ExportJobResponse exportPdf(Long manualId, LanguageCode languageCode);

    Path resolvePdfPath(Long exportJobId);
}
