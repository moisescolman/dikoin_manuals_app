package com.dikoin.manuals.dtos.reusableblock;

import com.dikoin.manuals.enums.ReusableType;
import jakarta.validation.constraints.NotBlank;

public record ReusableBlockRequest(
        @NotBlank String code,
        @NotBlank String title,
        String description,
        ReusableType reusableType,
        String productCategory,
        String productCodes,
        @NotBlank String contentJson,
        boolean active
) {
}
