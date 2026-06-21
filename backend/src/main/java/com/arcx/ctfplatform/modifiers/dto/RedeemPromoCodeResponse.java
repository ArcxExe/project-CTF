package com.arcx.ctfplatform.modifiers.dto;

public record RedeemPromoCodeResponse(
    boolean accepted,
    String message,
    int bonusPoints,
    int bonusAttempts
) {}
