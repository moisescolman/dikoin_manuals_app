package com.dikoin.manuals.dtos.notice;

public record NoticeUsageResponse(
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
