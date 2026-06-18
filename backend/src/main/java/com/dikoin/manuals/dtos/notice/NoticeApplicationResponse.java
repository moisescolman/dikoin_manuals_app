package com.dikoin.manuals.dtos.notice;

import com.dikoin.manuals.enums.NoticeApplyScope;

import java.time.LocalDateTime;

public record NoticeApplicationResponse(
        Long id,
        Long noticeTemplateId,
        Long manualId,
        Long productId,
        Long sectionId,
        NoticeApplyScope applyScope,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}
