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
        int grade,
        double maxKt1,
        double maxKt2,
        double maxKt3,
        double maxKt4,
        double maxTotalKt,
        double maxTotalPoints
) {
    public GradebookRowDto(
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
        this(
                studentId, rowNumber, pairNumber, fullName, studentCode, groupId, groupName, status,
                labs, totalLab, acceptedLabsCount, isEligible, lPercent,
                kt1, kt2, kt3, kt4, totalKt, kPercent, totalPoints,
                ctfScore, ctfCoeff, cPercent, token, sScore, examScore, recommendedGrade, grade,
                25.0, 25.0, 25.0, 25.0, 100.0, 243.0
        );
    }

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
