package com.arcx.ctfplatform.tests.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.entity.TestType;

public record TestResponse(
        UUID id,
        String title,
        String description,
        TestStatus status,
        Integer timeLimitMinutes,
        Integer passingScore,
        Integer questionsCount,
        UUID competitionId,
        Instant createdAt,
        Instant updatedAt,
        TestType type,
        Integer ktNumber
) {
    public TestResponse(UUID id, String title, String description, TestStatus status,
                        Integer timeLimitMinutes, Integer passingScore, Integer questionsCount,
                        UUID competitionId, Instant createdAt, Instant updatedAt, TestType type) {
        this(id, title, description, status, timeLimitMinutes, passingScore, questionsCount, competitionId, createdAt, updatedAt, type, null);
    }

    public TestResponse(UUID id, String title, String description, TestStatus status,
                        Integer timeLimitMinutes, Integer passingScore, Integer questionsCount,
                        UUID competitionId, Instant createdAt, Instant updatedAt) {
        this(id, title, description, status, timeLimitMinutes, passingScore, questionsCount, competitionId, createdAt, updatedAt, TestType.LECTURE, null);
    }

    @Component
    public static class Mapper implements IMapping<Test, TestResponse> {
        @Override
        public TestResponse mapping(Test from) {
            if (from == null) {
                return null;
            }
            return new TestResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getStatus(),
                    from.getTimeLimitMinutes(),
                    from.getPassingScore(),
                    from.getQuestionsCount(),
                    from.getCompetitionId(),
                    from.getCreatedAt(),
                    from.getUpdatedAt(),
                    from.getType() != null ? from.getType() : TestType.LECTURE,
                    from.getEffectiveKtNumber()
            );
        }
    }
}
