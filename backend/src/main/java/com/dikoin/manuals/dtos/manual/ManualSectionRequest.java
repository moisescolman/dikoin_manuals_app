package com.dikoin.manuals.dtos.manual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ManualSectionRequest(
        Long id,
        @NotNull Integer sortOrder,
        String sectionNumber,
        Long parentSectionId,
        Integer level,
        @NotBlank String titleEs,
        String titleEn,
        String completionStatus,
        @Valid List<ManualBlockRequest> blocks
) {
}
