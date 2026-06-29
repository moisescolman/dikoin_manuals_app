package com.dikoin.manuals.dtos.reusablefragment;

import java.time.LocalDateTime;

public record ReusableFragmentResponse(
        Long id,
        String code,
        String title,
        String description,
        String productCategory,
        String productCodes,
        String contentJson,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
