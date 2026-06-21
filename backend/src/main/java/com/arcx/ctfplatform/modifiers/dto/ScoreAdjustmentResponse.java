package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;

public record ScoreAdjustmentResponse(
        UUID id,
        UUID studentId,
        UUID competitionId,
        Integer points,
        String reason,
        Instant createdAt
) {
    @Component
    public static class Mapper implements IMapping<ScoreAdjustment, ScoreAdjustmentResponse> {
        @Override
        public ScoreAdjustmentResponse mapping(ScoreAdjustment from) {
            if (from == null) {
                return null;
            }
            return new ScoreAdjustmentResponse(
                    from.getId(),
                    from.getStudentId(),
                    from.getCompetitionId(),
                    from.getPoints(),
                    from.getReason(),
                    from.getCreatedAt()
            );
        }
    }
}
