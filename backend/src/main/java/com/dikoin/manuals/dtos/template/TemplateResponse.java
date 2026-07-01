package com.dikoin.manuals.dtos.template;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String description,
        String companyName,
        String contactEmail,
        String contactPhone,
        String website,
        String logoPath,
        Long logoAssetId,
        String logoUrl,
        String headerConfigJson,
        String footerConfigJson,
        String layoutConfigJson,
        boolean active,
        boolean systemDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
