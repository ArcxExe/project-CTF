package com.arcx.ctfplatform.students.dto.lab;

import java.util.UUID;

import lombok.Builder;

@Builder
public record LabScoreResponse(
    UUID studentId,
    String studentCode,
    String studentName,
    String groupName,
    String status,
    int score,
    double v1Coefficient
) {
}
