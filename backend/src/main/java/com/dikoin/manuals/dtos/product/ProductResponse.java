package com.dikoin.manuals.dtos.product;

import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String nameEs,
        String nameEn,
        Long familyId,
        String familyCode,
        String family,
        ProductFamilyResponse familyInfo,
        List<Long> categoryIds,
        String categoryCodes,
        String category,
        List<ProductCategoryResponse> categories,
        String description,
        String descriptionEs,
        String descriptionEn,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
