package com.dikoin.manuals.dtos.reusableblock;

import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.LanguageCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReusableFragmentBlockRequest(
        @NotNull BlockType blockType,
        @NotNull LanguageCode languageCode,
        @NotBlank String contentJson,
        String plainText,
        Long assetId,
        Integer sortOrder
) {
}
