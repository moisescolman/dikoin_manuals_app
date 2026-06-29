package com.dikoin.manuals.dtos.product;

import java.util.List;

public record ProductApplyImageRequest(
        List<Long> manualIds
) {
}
