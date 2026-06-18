package com.dikoin.manuals.dtos.search;

public record SearchSuggestionResponse(
        String type,
        Long id,
        String label,
        String description,
        String targetRoute
) {
}
