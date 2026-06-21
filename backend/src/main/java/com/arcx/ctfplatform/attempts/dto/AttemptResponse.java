package com.arcx.ctfplatform.attempts.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.attempts.entity.Attempt;

public record AttemptResponse(
        UUID id,
        UUID challengeId,
        UUID studentId,
        String submittedFlag,
        boolean isCorrect,
        String filePath,
        Integer scoreAwarded,
        Instant submittedAt
) {
    @Component
    public static class Mapper implements IMapping<Attempt, AttemptResponse> {
        @Override
        public AttemptResponse mapping(Attempt from) {
            if (from == null) {
                return null;
            }
            return new AttemptResponse(
                    from.getId(),
                    from.getChallengeId(),
                    from.getStudentId(),
                    from.getSubmittedFlag(),
                    from.isCorrect(),
                    from.getFilePath(),
                    from.getScoreAwarded(),
                    from.getSubmittedAt()
            );
        }
    }
}
