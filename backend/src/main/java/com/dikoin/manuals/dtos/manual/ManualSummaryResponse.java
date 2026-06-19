package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.ManualStatus;

import java.time.LocalDateTime;

public record ManualSummaryResponse(
        Long id,
        String code,
        String title,
        String titleEs,
        String titleEn,
        String category,
        boolean enabled,
        Long documentTypeId,
        String documentTypeCode,
        String documentTypeName,
        String documentYear,
        String documentVersion,
        String languageCode,
        Long productId,
        String productCode,
        String productName,
        Long activeVersionId,
        String activeVersionNumber,
        ManualStatus activeStatus,
        boolean esReady,
        boolean enReady,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
