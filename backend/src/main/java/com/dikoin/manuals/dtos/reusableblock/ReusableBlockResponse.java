package com.dikoin.manuals.dtos.reusableblock;

import java.time.LocalDateTime;

public record ReusableBlockResponse(
        Long id,
        String code,
        String title,
        String productCategory,
        String productCodes,
        String contentJson,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
