package com.dikoin.manuals.dtos.reusableblock;

import com.dikoin.manuals.enums.ReusableType;

import java.time.LocalDateTime;

public record ReusableBlockResponse(
        Long id,
        String code,
        String title,
        String description,
        ReusableType reusableType,
        String productCategory,
        String productCodes,
        String contentJson,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
