package com.arcx.ctfplatform.challenges.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.common.config.IMapping;

public record CtfTaskResponse(
        UUID id,
        String title,
        String description,
        String category,
        String difficulty,
        Integer baseScore,
        Instant createdAt,
        boolean isFirstBloodOnly,
        Integer maxScore,
        TaskType taskType,
        List<String> attachments,
        String taskCode,
        String bankId,
        String pool,
        Integer wave,
        long solvedCount,
        boolean firstBloodTaken
) {
    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt) {
        this(id, title, description, category, difficulty, baseScore, createdAt, false, baseScore, TaskType.FLAG, Collections.emptyList(), null, "GLOBAL", "MAIN", null, 0L, false);
    }

    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt, boolean isFirstBloodOnly, Integer maxScore) {
        this(id, title, description, category, difficulty, baseScore, createdAt, isFirstBloodOnly, maxScore, TaskType.FLAG, Collections.emptyList(), null, "GLOBAL", "MAIN", null, 0L, false);
    }

    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt, boolean isFirstBloodOnly, Integer maxScore, TaskType taskType, List<String> attachments) {
        this(id, title, description, category, difficulty, baseScore, createdAt, isFirstBloodOnly, maxScore, taskType, attachments, null, "GLOBAL", "MAIN", null, 0L, false);
    }

    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt, boolean isFirstBloodOnly, Integer maxScore, TaskType taskType, List<String> attachments, String taskCode, String bankId, String pool, Integer wave) {
        this(id, title, description, category, difficulty, baseScore, createdAt, isFirstBloodOnly, maxScore, taskType, attachments, taskCode, bankId, pool, wave, 0L, false);
    }

    public CtfTaskResponse withSolveStats(long solvedCount) {
        long safeSolved = Math.max(0L, solvedCount);
        return new CtfTaskResponse(
                id,
                title,
                description,
                category,
                difficulty,
                baseScore,
                createdAt,
                isFirstBloodOnly,
                maxScore,
                taskType,
                attachments,
                taskCode,
                bankId,
                pool,
                wave,
                safeSolved,
                safeSolved > 0
        );
    }

    @Component
    public static class Mapper implements IMapping<CtfTask, CtfTaskResponse> {

        @Override
        public CtfTaskResponse mapping(CtfTask from) {
            if (from == null) {
                return null;
            }
            List<String> attachmentList = Collections.emptyList();
            if (from.getAttachments() != null) {
                Object filesObj = from.getAttachments().get("files");
                if (filesObj instanceof List<?> list) {
                    attachmentList = list.stream().filter(Objects::nonNull).map(Object::toString).toList();
                } else if (!from.getAttachments().isEmpty()) {
                    attachmentList = from.getAttachments().values().stream().filter(Objects::nonNull).map(Object::toString).toList();
                }
            }
            return new CtfTaskResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getCategory(),
                    from.getDifficulty(),
                    from.getBaseScore(),
                    from.getCreatedAt(),
                    from.isFirstBloodOnly(),
                    from.getMaxScore() != null && from.getMaxScore() > 0 ? from.getMaxScore() : from.getBaseScore(),
                    from.getTaskType() != null ? from.getTaskType() : TaskType.FLAG,
                    attachmentList,
                    from.getTaskCode(),
                    from.getBankId() != null && !from.getBankId().isBlank() ? from.getBankId() : "GLOBAL",
                    from.getPool() != null && !from.getPool().isBlank() ? from.getPool() : "MAIN",
                    from.getWave(),
                    0L,
                    false
            );
        }
    }
}
