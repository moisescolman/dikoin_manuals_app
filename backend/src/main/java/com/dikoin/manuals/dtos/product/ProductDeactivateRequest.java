package com.dikoin.manuals.dtos.product;

import java.util.List;

public record ProductDeactivateRequest(
        List<Long> deactivateManualIds
) {
}
