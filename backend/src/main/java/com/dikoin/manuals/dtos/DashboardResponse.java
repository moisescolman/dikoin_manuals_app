package com.dikoin.manuals.dtos;

public record DashboardResponse(
        long products,
        long manuals,
        long publishedManuals,
        long draftManuals,
        long reviewManuals,
        long manualsWithEnglishPending
) {
}
