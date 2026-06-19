package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.documenttype.DocumentTypeResponse;
import com.dikoin.manuals.servicios.DocumentTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-types")
@RequiredArgsConstructor
public class DocumentTypeRestController {

    private final DocumentTypeService documentTypeService;

    @GetMapping
    public List<DocumentTypeResponse> findActive() {
        return documentTypeService.findActive();
    }
}
