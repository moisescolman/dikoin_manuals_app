package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.manual.ManualTranslationRequest;
import com.dikoin.manuals.dtos.manual.ManualTranslationResponse;

public interface ManualTranslationService {
    ManualTranslationResponse translateToEnglish(Long manualVersionId, ManualTranslationRequest request);
}
