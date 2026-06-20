package com.arcx.ctfplatform.attempts.dto;

public record ChallengeSubmitResponse(
        boolean isCorrect,
        String message
) {
}
