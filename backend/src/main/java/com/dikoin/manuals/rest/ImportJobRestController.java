package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.importjob.ImportJobResponse;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.servicios.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/import-jobs")
@RequiredArgsConstructor
public class ImportJobRestController {

    private final ImportService importService;

    @GetMapping
    public List<ImportJobResponse> findRecent() {
        return importService.findRecent();
    }

    @GetMapping("/{id}")
    public ImportJobResponse findById(@PathVariable Long id) {
        return importService.findById(id);
    }

    @PostMapping("/documents")
    public ImportJobResponse importDocument(
            @RequestParam MultipartFile file,
            @RequestParam Long productId,
            @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) String documentYear,
            @RequestParam(required = false) String documentVersion,
            @RequestParam(required = false) String manualCode,
            @RequestParam String title,
            @RequestParam(defaultValue = "ES") LanguageCode languageCode
    ) {
        return importService.importDocument(file, productId, documentTypeId, documentYear, documentVersion, manualCode, title, languageCode);
    }

    @PostMapping("/pdf")
    public ImportJobResponse importPdf(
            @RequestParam MultipartFile file,
            @RequestParam Long productId,
            @RequestParam(required = false) Long documentTypeId,
            @RequestParam(required = false) String documentYear,
            @RequestParam(required = false) String documentVersion,
            @RequestParam(required = false) String manualCode,
            @RequestParam String title,
            @RequestParam(defaultValue = "ES") LanguageCode languageCode
    ) {
        return importService.importDocument(file, productId, documentTypeId, documentYear, documentVersion, manualCode, title, languageCode);
    }
}
