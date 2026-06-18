package com.dikoin.manuals.dtos.importjob;

import com.dikoin.manuals.enums.ImportStatus;
import com.dikoin.manuals.enums.LanguageCode;

import java.time.LocalDateTime;

public record ImportJobResponse(
        Long id,
        String sourceFilename,
        String storedPath,
        String fileExtension,
        ImportStatus status,
        LanguageCode languageCode,
        Long manualVersionId,
        Integer detectedSections,
        Integer detectedTables,
        Integer detectedImages,
        String logMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
