package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.notice.BulkNoticeApplyRequest;
import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.dtos.notice.NoticeTemplateRequest;
import com.dikoin.manuals.dtos.notice.NoticeTemplateResponse;
import com.dikoin.manuals.dtos.notice.NoticeUsageResponse;
import com.dikoin.manuals.enums.NoticeType;

import java.util.List;

public interface NoticeService {
    List<NoticeTemplateResponse> findAll(NoticeType type, String search);
    NoticeTemplateResponse create(NoticeTemplateRequest request);
    NoticeTemplateResponse update(Long id, NoticeTemplateRequest request);
    List<NoticeUsageResponse> findUsages(Long id);
    void delete(Long id);
    List<NoticeApplicationResponse> apply(BulkNoticeApplyRequest request);
}
