package com.dikoin.manuals.dtos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 120) String family,
        @Size(max = 120) String category,
        @Size(max = 600) String description,
        Boolean active
) {
}
