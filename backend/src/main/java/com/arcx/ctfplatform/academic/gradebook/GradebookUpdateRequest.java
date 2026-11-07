package com.arcx.ctfplatform.academic.gradebook;

import java.util.Map;

public record GradebookUpdateRequest(
        Integer pairNumber,
        Map<Integer, Double> labScores,
        Map<Integer, Boolean> labAccepted,
        Double kt1,
        Double kt2,
        Double kt3,
        Double kt4,
        String token,
        Integer examScore
) {}
