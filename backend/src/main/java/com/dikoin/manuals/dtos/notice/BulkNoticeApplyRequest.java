package com.dikoin.manuals.dtos.notice;

import com.dikoin.manuals.enums.NoticeApplyScope;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkNoticeApplyRequest(
        Long noticeTemplateId,
        NoticeTemplateRequest notice,
        @NotNull NoticeApplyScope applyScope,
        List<Long> manualIds,
        List<Long> productIds,
        List<Long> sectionIds
) {
}
