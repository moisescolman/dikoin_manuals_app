package com.dikoin.manuals.dtos.manual;

import java.util.List;

public record ManualSectionResponse(
        Long id,
        Integer sortOrder,
        String sectionNumber,
        Long parentSectionId,
        Long linkedReusableSectionId,
        Integer level,
        String titleEs,
        String titleEn,
        String completionStatus,
        boolean visible,
        List<ManualBlockResponse> blocks
) {
}
