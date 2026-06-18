package com.dikoin.manuals.dtos.product;

import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String family,
        String category,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
