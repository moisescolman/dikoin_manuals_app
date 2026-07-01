package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.LanguageCode;

import java.util.List;

public record ManualTranslationResponse(
        String status,
        Long manualVersionId,
        LanguageCode targetLanguage,
        int translatedSections,
        int translatedBlocks,
        int skippedSections,
        List<String> errors
) {
}
