package com.dikoin.manuals.dtos.manual;

import java.util.List;

public record ManualSectionResponse(
        Long id,
        Integer sortOrder,
        String sectionNumber,
        String titleEs,
        String titleEn,
        String completionStatus,
        List<ManualBlockResponse> blocks
) {
}
