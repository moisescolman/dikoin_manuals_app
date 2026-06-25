package com.dikoin.manuals.dtos.manual;

public record ManualSectionPatchRequest(
        String titleEs,
        String titleEn,
        String completionStatus,
        Boolean visible
) {
}
