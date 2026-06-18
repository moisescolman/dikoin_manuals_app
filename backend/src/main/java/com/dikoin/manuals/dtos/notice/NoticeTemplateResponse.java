package com.dikoin.manuals.dtos.notice;

import com.dikoin.manuals.enums.NoticeType;

import java.time.LocalDateTime;

public record NoticeTemplateResponse(
        Long id,
        String code,
        NoticeType type,
        String titleEs,
        String titleEn,
        String productCategory,
        String productCodes,
        String contentEs,
        String contentEn,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
