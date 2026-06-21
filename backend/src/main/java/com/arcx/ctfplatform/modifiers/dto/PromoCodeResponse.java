package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(
    UUID id,
    String code,
    String title,
    String description,
    Integer bonusPoints,
    Integer bonusAttempts,
    Integer maxUses,
    Integer usedCount,
    String status,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {}
