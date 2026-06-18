package com.dikoin.manuals.dtos.manual;

import com.dikoin.manuals.enums.ManualStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ManualVersionRequest(
        @NotBlank String versionNumber,
        ManualStatus status,
        boolean active,
        boolean esReady,
        boolean enReady,
        String changeNotes,
        @Valid List<ManualSectionRequest> sections
) {
}
