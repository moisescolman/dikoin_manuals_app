package com.dikoin.manuals.dtos.template;

import jakarta.validation.constraints.NotBlank;

public record TemplateRequest(
        @NotBlank String name,
        String description,
        String companyName,
        String contactEmail,
        String contactPhone,
        String website,
        String logoPath,
        Long logoAssetId,
        String headerConfigJson,
        String footerConfigJson,
        String layoutConfigJson,
        boolean active
) {
}
