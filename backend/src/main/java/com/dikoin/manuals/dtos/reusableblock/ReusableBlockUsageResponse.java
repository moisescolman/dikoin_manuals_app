package com.dikoin.manuals.dtos.reusableblock;

public record ReusableBlockUsageResponse(
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
