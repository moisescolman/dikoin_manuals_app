package com.dikoin.manuals.dtos.notice;

import com.dikoin.manuals.enums.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoticeTemplateRequest(
        @NotBlank String code,
        @NotNull NoticeType type,
        @NotBlank String titleEs,
        String titleEn,
        @NotBlank String contentEs,
        String contentEn,
        boolean active
) {
}
