package com.dikoin.manuals.servicios;

import com.dikoin.manuals.enums.BlockType;

import java.util.List;
import java.util.Map;

public interface GeminiTranslationService {
    Map<String, String> translateToEnglish(List<TranslationTextItem> items);

    record TranslationTextItem(
            String id,
            BlockType blockType,
            String translatableText
    ) {
    }
}
