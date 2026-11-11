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
        List<String> attachments
) {
    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt) {
        this(id, title, description, category, difficulty, baseScore, createdAt, false, baseScore, TaskType.FLAG, Collections.emptyList());
    }

    public CtfTaskResponse(UUID id, String title, String description, String category, String difficulty, Integer baseScore, Instant createdAt, boolean isFirstBloodOnly, Integer maxScore) {
        this(id, title, description, category, difficulty, baseScore, createdAt, isFirstBloodOnly, maxScore, TaskType.FLAG, Collections.emptyList());
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
                    attachmentList
            );
        }
    }
}
