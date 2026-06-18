package com.dikoin.manuals.dtos.auth;

public record LoginResponse(
        Long userId,
        String fullName,
        String email,
        String role,
        String token
) {
}
