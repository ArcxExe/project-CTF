package com.arcx.ctfplatform.competitions.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;

public record CompetitionResponse(
        UUID id,
        String title,
        String description,
        Instant startDate,
        Instant endDate,
        CompetitionStatus status,
        Instant createdAt,
        boolean sumTestPoints,
        boolean leaderboardHidden,
        List<UUID> hiddenStudentIds,
        List<CtfTaskResponse> tasks
) {
    @Component
    public static class Mapper implements IMapping<Competition, CompetitionResponse> {
        
        private final CtfTaskResponse.Mapper taskMapper = new CtfTaskResponse.Mapper();

        @Override
        public CompetitionResponse mapping(Competition from) {
            if (from == null) {
                return null;
            }
            
            List<CtfTaskResponse> taskResponses = from.getTasks() != null 
                    ? from.getTasks().stream().map(taskMapper::mapping).collect(Collectors.toList())
                    : Collections.emptyList();

            return new CompetitionResponse(
                    from.getId(),
                    from.getTitle(),
                    from.getDescription(),
                    from.getStartDate(),
                    from.getEndDate(),
                    from.getStatus(),
                    from.getCreatedAt(),
                    from.isSumTestPoints(),
                    from.isLeaderboardHidden(),
                    from.getHiddenStudentIds(),
                    taskResponses
            );
        }
    }
}
