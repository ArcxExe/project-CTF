package com.arcx.ctfplatform.challenges.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.common.config.IMapping;

public record CtfTaskResponse(
        UUID id,
        String title,
        String description,
        String category,
        String difficulty,
        Integer baseScore,
        Instant createdAt
) {
    @Component
    public static class Mapper implements IMapping<CtfTask, CtfTaskResponse> {

        @Override
        public CtfTaskResponse mapping(CtfTask from) {
            if (from == null) {
                return null;
            }
            return new CtfTaskResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getCategory(),
                    from.getDifficulty(),
                    from.getBaseScore(),
                    from.getCreatedAt()
            );
        }
    }
}
