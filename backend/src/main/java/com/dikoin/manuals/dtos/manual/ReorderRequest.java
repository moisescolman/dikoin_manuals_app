package com.dikoin.manuals.dtos.manual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
