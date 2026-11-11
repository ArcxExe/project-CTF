package com.arcx.ctfplatform.challenges.dto;

import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.List;

public record CtfTaskRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String category,
        String difficulty,
        @NotNull @Min(1) Integer baseScore,
        String flag,
        Boolean isFirstBloodOnly,
        Integer maxScore,
        TaskType taskType,
        List<String> attachments
) {
    public CtfTaskRequest(String title, String description, String category, String difficulty, Integer baseScore, String flag) {
        this(title, description, category, difficulty, baseScore, flag, false, baseScore, TaskType.FLAG, Collections.emptyList());
    }

    public CtfTaskRequest(String title, String description, String category, String difficulty, Integer baseScore, String flag, Boolean isFirstBloodOnly, Integer maxScore) {
        this(title, description, category, difficulty, baseScore, flag, isFirstBloodOnly, maxScore, TaskType.FLAG, Collections.emptyList());
    }

    @JsonIgnore
    @AssertTrue(message = "Flag is required for FLAG tasks")
    public boolean isFlagValid() {
        TaskType effectiveType = taskType != null ? taskType : TaskType.FLAG;
        if (effectiveType == TaskType.FILE_UPLOAD) {
            return true;
        }
        return flag != null && !flag.isBlank();
    }
}
