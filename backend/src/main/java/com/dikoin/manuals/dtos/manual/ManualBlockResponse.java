package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.LanguageCode;

public record ManualBlockResponse(
        Long id,
        Integer sortOrder,
        BlockType blockType,
        LanguageCode languageCode,
        String contentJson,
        String plainText,
        Long assetId,
        Long reusableBlockId,
        Long reusableFragmentId
) {
}
