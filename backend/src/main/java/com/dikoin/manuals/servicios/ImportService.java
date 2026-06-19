package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.importjob.ImportJobResponse;
import com.dikoin.manuals.enums.LanguageCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImportService {
    ImportJobResponse importDocument(
            MultipartFile file,
            Long productId,
            Long documentTypeId,
            String documentYear,
            String documentVersion,
            String manualCode,
            String title,
            LanguageCode languageCode
    );
    ImportJobResponse findById(Long id);
    List<ImportJobResponse> findRecent();
}
