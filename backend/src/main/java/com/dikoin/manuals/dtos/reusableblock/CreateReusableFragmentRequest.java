package com.dikoin.manuals.dtos.reusableblock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateReusableFragmentRequest(
        @NotBlank String name,
        String description,
        Long sourceSectionId,
        List<Long> blockIds,
        @Valid List<ReusableFragmentBlockRequest> blocks,
        boolean isReusable
) {
}
