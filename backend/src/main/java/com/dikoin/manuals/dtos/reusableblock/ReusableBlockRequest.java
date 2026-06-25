package com.dikoin.manuals.dtos.reusableblock;

import com.dikoin.manuals.enums.ReusableType;
public record ReusableBlockRequest(
        String code,
        String title,
        String titleEs,
        String titleEn,
        String description,
        String descriptionEs,
        String descriptionEn,
        ReusableType reusableType,
        String productCategory,
        String productCodes,
        String contentJson,
        boolean active
) {
}
