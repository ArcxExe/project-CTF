package com.arcx.ctfplatform.tests.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.entity.TestType;

public record TestRequest(
        @NotBlank String title,
        String description,
        @NotNull TestStatus status,
        @NotNull @Min(1) Integer timeLimitMinutes,
        @NotNull @Min(0) Integer passingScore,
        @NotNull @Min(0) Integer questionsCount,
        UUID competitionId,
        TestType type,
        Integer ktNumber
) {
    public TestRequest(String title, String description, TestStatus status,
                       Integer timeLimitMinutes, Integer passingScore,
                       Integer questionsCount, UUID competitionId, TestType type) {
        this(title, description, status, timeLimitMinutes, passingScore, questionsCount, competitionId, type, null);
    }

    public TestRequest(String title, String description, TestStatus status,
                       Integer timeLimitMinutes, Integer passingScore,
                       Integer questionsCount, UUID competitionId) {
        this(title, description, status, timeLimitMinutes, passingScore, questionsCount, competitionId, TestType.LECTURE, null);
    }
}
