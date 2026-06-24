package com.dikoin.manuals.dtos.reusableblock;

import com.dikoin.manuals.dtos.manual.ManualBlockResponse;

import java.util.List;

public record ReusableFragmentInsertResponse(
        Long reusableBlockId,
        Long targetSectionId,
        List<ManualBlockResponse> blocks
) {
}
