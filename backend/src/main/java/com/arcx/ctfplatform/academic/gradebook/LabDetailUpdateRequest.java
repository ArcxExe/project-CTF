package com.arcx.ctfplatform.academic.gradebook;

import java.util.UUID;

public record LabDetailUpdateRequest(
        UUID studentId,
        int labNumber,
        double rawScore,
        double defenseScore,
        double penaltyCoefficient,
        Boolean acceptedOverride
) {}
