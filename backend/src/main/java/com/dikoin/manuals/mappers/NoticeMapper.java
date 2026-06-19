package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.dtos.notice.NoticeTemplateResponse;
import com.dikoin.manuals.dtos.notice.NoticeUsageResponse;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.NoticeApplication;
import com.dikoin.manuals.entidades.NoticeTemplate;
import org.springframework.stereotype.Component;

@Component
public class NoticeMapper {
    public NoticeTemplateResponse toTemplateResponse(NoticeTemplate notice) {
        return new NoticeTemplateResponse(
                notice.getId(),
                notice.getCode(),
                notice.getType(),
                notice.getTitleEs(),
                notice.getTitleEn(),
                notice.getVisibleTitleEs(),
                notice.getVisibleTitleEn(),
                notice.getProductCategory(),
                notice.getProductCodes(),
                notice.getContentEs(),
                notice.getContentEn(),
                notice.isActive(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }

    public NoticeApplicationResponse toApplicationResponse(NoticeApplication application) {
        return new NoticeApplicationResponse(
                application.getId(),
                application.getNoticeTemplate().getId(),
                application.getManual() != null ? application.getManual().getId() : null,
                application.getProduct() != null ? application.getProduct().getId() : null,
                application.getSection() != null ? application.getSection().getId() : null,
                application.getApplyScope(),
                application.getSortOrder(),
                application.getCreatedAt()
        );
    }

    public NoticeUsageResponse toUsageResponse(ManualBlock block) {
        ManualSection section = block.getSection();
        Manual manual = section.getManualVersion().getManual();
        return new NoticeUsageResponse(
                manual.getId(),
                manual.getCode(),
                manual.getTitle(),
                manual.getProduct().getCode(),
                section.getId(),
                section.getSectionNumber(),
                section.getTitleEs(),
                block.getId()
        );
    }
}
