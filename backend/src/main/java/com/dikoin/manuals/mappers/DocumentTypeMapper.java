package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.documenttype.DocumentTypeResponse;
import com.dikoin.manuals.entidades.DocumentType;
import org.springframework.stereotype.Component;

@Component
public class DocumentTypeMapper {
    public DocumentTypeResponse toResponse(DocumentType type) {
        return new DocumentTypeResponse(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.isActive(),
                type.getSortOrder()
        );
    }
}
