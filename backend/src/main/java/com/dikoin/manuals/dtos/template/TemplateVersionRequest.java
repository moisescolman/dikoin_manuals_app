package com.dikoin.manuals.dtos.template;

import jakarta.validation.constraints.NotBlank;

public record TemplateVersionRequest(
        @NotBlank String versionNumber,
        @NotBlank String configJson,
        boolean active,
        String notes
) {
}
