package com.dikoin.manuals.dtos.reusableblock;

import jakarta.validation.constraints.NotNull;

public record InsertReusableFragmentRequest(
        @NotNull Long targetSectionId,
        Long insertAfterBlockId,
        String mode
) {
}
