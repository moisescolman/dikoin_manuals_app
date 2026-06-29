package com.dikoin.manuals.dtos.reusablefragment;

import com.dikoin.manuals.dtos.manual.ManualBlockResponse;

import java.util.List;

public record ReusableFragmentInsertResponse(
        Long reusableFragmentId,
        Long targetSectionId,
        List<ManualBlockResponse> insertedBlocks
) {
}
