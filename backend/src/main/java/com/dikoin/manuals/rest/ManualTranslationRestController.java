package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.manual.ManualTranslationRequest;
import com.dikoin.manuals.dtos.manual.ManualTranslationResponse;
import com.dikoin.manuals.servicios.ManualTranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manual-versions")
@RequiredArgsConstructor
public class ManualTranslationRestController {

    private final ManualTranslationService manualTranslationService;

    @PostMapping("/{manualVersionId}/translate/en")
    public ManualTranslationResponse translateToEnglish(
            @PathVariable Long manualVersionId,
            @RequestBody(required = false) ManualTranslationRequest request
    ) {
        return manualTranslationService.translateToEnglish(manualVersionId, request);
    }
}
