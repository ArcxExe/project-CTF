package com.arcx.ctfplatform.attempts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record AttemptHistoryResponse(
    UUID attemptId,
    UUID taskId,
    String challengeTitle,
    boolean correct,
    Integer earnedPoints,
    Instant submittedAt
) {
    public AttemptHistoryResponse {
        if (earnedPoints == null) {
            earnedPoints = 0;
        }
    }

    @JsonProperty("challengeId")
    public UUID getChallengeId() {
        return taskId;
    }
}
