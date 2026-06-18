package com.dikoin.manuals.dtos.manual;

import java.time.LocalDateTime;
import java.util.List;

public record ManualDetailResponse(
        Long id,
        String code,
        String title,
        String titleEs,
        String titleEn,
        String category,
        boolean enabled,
        Long productId,
        String productCode,
        String productName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        ManualVersionResponse activeVersion,
        List<ManualVersionResponse> versions
) {
}
