package com.arcx.ctfplatform.auth.dto;

import com.arcx.ctfplatform.users.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMillis,
        UUID userId,
        String email,
        Role role
) {
}
