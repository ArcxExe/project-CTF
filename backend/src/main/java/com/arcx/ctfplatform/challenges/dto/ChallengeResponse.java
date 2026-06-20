package com.arcx.ctfplatform.challenges.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.challenges.entity.Challenge;
import com.arcx.ctfplatform.common.config.IMapping;

public record ChallengeResponse(
        UUID id,
        String title,
        String description,
        Integer points,
        UUID competitionId,
        Instant createdAt
) {
    @Component
    public static class Mapper implements IMapping<Challenge, ChallengeResponse> {

        @Override
        public ChallengeResponse mapping(Challenge from) {
            if (from == null) {
                return null;
            }
            return new ChallengeResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getPoints(),
                    from.getCompetitionId(),
                    from.getCreatedAt()
            );
        }
    }
}
