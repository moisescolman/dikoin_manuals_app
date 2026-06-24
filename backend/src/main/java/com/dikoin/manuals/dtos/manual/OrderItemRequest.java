package com.dikoin.manuals.dtos.manual;

import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long id,
        @NotNull Integer sortOrder
) {
}
