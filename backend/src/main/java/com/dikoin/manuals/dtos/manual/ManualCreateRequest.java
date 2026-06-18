package com.dikoin.manuals.dtos.manual;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 220) String titleEs,
        @Size(max = 220) String titleEn,
        @Size(max = 120) String category,
        @NotNull Long productId
) {
}
