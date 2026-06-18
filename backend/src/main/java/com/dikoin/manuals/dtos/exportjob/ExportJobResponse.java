package com.dikoin.manuals.dtos.exportjob;

import com.dikoin.manuals.enums.ExportStatus;

import java.time.LocalDateTime;

public record ExportJobResponse(
        Long id,
        Long manualVersionId,
        ExportStatus status,
        String pdfPath,
        String logMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
