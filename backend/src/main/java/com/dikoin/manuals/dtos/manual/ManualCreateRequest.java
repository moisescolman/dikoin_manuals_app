package com.dikoin.manuals.dtos.manual;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualCreateRequest(
        @Size(max = 100) String code,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 220) String titleEs,
        @Size(max = 220) String titleEn,
        @Size(max = 120) String category,
        @NotNull Long productId,
        Long documentTypeId,
        @Size(min = 2, max = 2) String documentYear,
        @Size(min = 2, max = 2) String documentVersion,
        @Size(min = 2, max = 10) String languageCode
) {
}
