package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.ManualStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ManualVersionResponse(
        Long id,
        String versionNumber,
        ManualStatus status,
        boolean active,
        boolean esReady,
        boolean enReady,
        String changeNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime publishedAt,
        List<ManualSectionResponse> sections
) {
}
