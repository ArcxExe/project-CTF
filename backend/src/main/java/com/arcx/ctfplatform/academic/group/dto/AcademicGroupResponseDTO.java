package com.arcx.ctfplatform.academic.group.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.common.config.IMapping;

public record AcademicGroupResponseDTO(
        String name,
        UUID id,
        Instant createdAt,
        UUID streamId) {

    @Component
    public static class Mapper implements IMapping<AcademicGroup, AcademicGroupResponseDTO> {

        @Override
        public AcademicGroupResponseDTO mapping(AcademicGroup from) {
            if (from == null) {
                return null;
            }
            // Точка с запятой добавлена в конец строки
            return new AcademicGroupResponseDTO(
                    from.getName(),
                    from.getId(),
                    from.getCreatedAt(),
                    from.getStreamId());
        }
    }
}
