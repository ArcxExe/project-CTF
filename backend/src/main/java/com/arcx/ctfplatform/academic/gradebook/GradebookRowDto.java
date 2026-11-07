package com.arcx.ctfplatform.academic.gradebook;

import java.util.Map;
import java.util.UUID;

public record GradebookRowDto(
        UUID studentId,
        int rowNumber,
        Integer pairNumber,
        String fullName,
        String studentCode,
        UUID groupId,
        String groupName,
        String status,
        Map<Integer, LabCellDto> labs,
        double totalLab,
        int acceptedLabsCount,
        boolean isEligible,
        double lPercent,
        double kt1,
        double kt2,
        double kt3,
        double kt4,
        double totalKt,
        double kPercent,
        double totalPoints,
        int ctfScore,
        double ctfCoeff,
        double cPercent,
        String token,
        double sScore,
        Integer examScore,
        Integer recommendedGrade,
        int grade
) {
    public record LabCellDto(
            int labNumber,
            double score,
            double maxScore,
            double rawScore,
            double maxRawScore,
            double defenseScore,
            double maxDefenseScore,
            double penaltyCoefficient,
            boolean accepted
    ) {}
}
