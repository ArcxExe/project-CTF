package com.arcx.ctfplatform.competitions.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;

public record CompetitionResponse(
        UUID id,
        String title,
        String description,
        Instant startsAt,
        Instant endsAt,
        CompetitionStatus status,
        Instant createdAt
) {
    @Component
    public static class Mapper implements IMapping<Competition, CompetitionResponse> {
        @Override
        public CompetitionResponse mapping(Competition from) {
            if (from == null) {
                return null;
            }
            return new CompetitionResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getStartsAt(),
                    from.getEndsAt(),
                    from.getStatus(),
                    from.getCreatedAt()
            );
        }
    }
}
