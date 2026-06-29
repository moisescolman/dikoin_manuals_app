package com.dikoin.manuals.dtos.reusablefragment;

public record ReusableFragmentUsageResponse(
        Long manualId,
        String manualCode,
        String manualTitle,
        String productCode,
        Long sectionId,
        String sectionNumber,
        String sectionTitle,
        Long blockId
) {
}
