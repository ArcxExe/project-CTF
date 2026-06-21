package com.arcx.ctfplatform.attempts.dto;

import java.time.Instant;
import java.util.UUID;

public record AttemptHistoryResponse(
    UUID attemptId,
    UUID challengeId,
    String challengeTitle,
    boolean correct,
    int earnedPoints,
    Instant submittedAt
) {}
