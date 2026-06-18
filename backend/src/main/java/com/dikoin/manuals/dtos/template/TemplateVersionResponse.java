package com.dikoin.manuals.dtos.template;

import java.time.LocalDateTime;

public record TemplateVersionResponse(
        Long id,
        Long templateId,
        String versionNumber,
        String configJson,
        boolean active,
        String notes,
        LocalDateTime createdAt
) {
}
