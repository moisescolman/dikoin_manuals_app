package com.dikoin.manuals.dtos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String nameEs,
        @Size(max = 160) String nameEn,
        Long familyId,
        @Size(max = 120) String family,
        @Size(max = 20) String familyCode,
        List<Long> categoryIds,
        @Size(max = 120) String category,
        @Size(max = 120) String categoryCodes,
        @Size(max = 600) String description,
        @Size(max = 600) String descriptionEs,
        @Size(max = 600) String descriptionEn,
        Boolean active
) {
}
