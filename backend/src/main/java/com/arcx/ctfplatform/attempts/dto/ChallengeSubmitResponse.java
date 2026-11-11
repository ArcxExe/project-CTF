package com.arcx.ctfplatform.attempts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChallengeSubmitResponse(
        @JsonProperty("isCorrect") boolean isCorrect,
        String message
) {
    @JsonProperty("correct")
    public boolean getCorrect() {
        return isCorrect;
    }
}
