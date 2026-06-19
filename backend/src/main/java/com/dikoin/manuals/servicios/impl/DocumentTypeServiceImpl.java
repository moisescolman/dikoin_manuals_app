package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.documenttype.DocumentTypeResponse;
import com.dikoin.manuals.mappers.DocumentTypeMapper;
import com.dikoin.manuals.repositorios.DocumentTypeRepository;
import com.dikoin.manuals.servicios.DocumentTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentTypeServiceImpl implements DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentTypeMapper documentTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> findActive() {
        return documentTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(documentTypeMapper::toResponse)
                .toList();
    }
}
