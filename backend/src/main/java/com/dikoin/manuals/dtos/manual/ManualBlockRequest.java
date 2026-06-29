package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.LanguageCode;
import jakarta.validation.constraints.NotNull;

public record ManualBlockRequest(
        Long id,
        @NotNull Integer sortOrder,
        @NotNull BlockType blockType,
        @NotNull LanguageCode languageCode,
        @NotNull String contentJson,
        String plainText,
        Long assetId,
        Long reusableBlockId,
        Long reusableFragmentId
) {
}
