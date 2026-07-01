package com.dikoin.manuals.dtos.manual;

import java.util.List;

public record ManualTranslationRequest(
        ManualTranslationMode mode,
        List<Long> sectionIds,
        Boolean overwriteExisting
) {
}
