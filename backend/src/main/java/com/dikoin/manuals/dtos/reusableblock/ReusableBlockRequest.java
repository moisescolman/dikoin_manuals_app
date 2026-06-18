package com.dikoin.manuals.dtos.reusableblock;

import jakarta.validation.constraints.NotBlank;

public record ReusableBlockRequest(
        @NotBlank String code,
        @NotBlank String title,
        String productCategory,
        String productCodes,
        @NotBlank String contentJson,
        boolean active
) {
}
