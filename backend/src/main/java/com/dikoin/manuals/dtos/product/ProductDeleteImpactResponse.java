package com.dikoin.manuals.dtos.product;

import com.dikoin.manuals.dtos.manual.ManualSummaryResponse;

import java.util.List;

public record ProductDeleteImpactResponse(
        Long productId,
        boolean hasManualHistory,
        List<ManualSummaryResponse> relatedManuals,
        List<ManualSummaryResponse> activeManuals
) {
}
