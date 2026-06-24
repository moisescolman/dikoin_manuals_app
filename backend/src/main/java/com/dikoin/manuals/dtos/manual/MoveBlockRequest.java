package com.dikoin.manuals.dtos.manual;

import jakarta.validation.constraints.NotNull;

public record MoveBlockRequest(
        @NotNull Long targetSectionId,
        Long insertAfterBlockId
) {
}
