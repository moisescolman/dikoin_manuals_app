package com.dikoin.manuals.dtos.manual;

import jakarta.validation.constraints.Size;

public record ManualRestoreRequest(
        @Size(max = 220) String title,
        @Size(max = 220) String titleEs,
        @Size(max = 220) String titleEn
) {
}
