package com.dikoin.manuals.dtos.documenttype;

public record DocumentTypeResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        Integer sortOrder
) {
}
