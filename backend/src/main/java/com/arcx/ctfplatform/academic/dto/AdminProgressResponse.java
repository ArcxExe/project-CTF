package com.arcx.ctfplatform.academic.dto;

import java.util.UUID;

public record AdminProgressResponse(
        UUID studentId,
        String username,
        String groupName,
        int totalScore,
        int solvedCount,
        int attemptsCount
) {}
