package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;

public record PromoCodeCreateRequest(
    String code,
    String title,
    String description,
    Integer bonusPoints,
    Integer bonusAttempts,
    Integer maxUses,
    String status,
    Instant expiresAt
) {}
