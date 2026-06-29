package com.dikoin.manuals.dtos.notice;

import com.dikoin.manuals.enums.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoticeTemplateRequest(
        @Size(max = 80) String code,
        @NotNull NoticeType type,
        @NotBlank String title,
        String visibleTitleEs,
        String visibleTitleEn,
        String productCategory,
        String productCodes,
        @NotBlank String contentEs,
        String contentEn,
        boolean active
) {
}
