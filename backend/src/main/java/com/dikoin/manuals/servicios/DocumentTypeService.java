package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.documenttype.DocumentTypeResponse;

import java.util.List;

public interface DocumentTypeService {
    List<DocumentTypeResponse> findActive();
}
