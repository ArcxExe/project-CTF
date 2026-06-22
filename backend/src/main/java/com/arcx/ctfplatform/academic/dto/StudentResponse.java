package com.arcx.ctfplatform.academic.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.common.config.IMapping;

public record StudentResponse(
        UUID id,
        String firstName,
        String lastName,
        String middleName,
        String studentCode,
        UUID userId,
        UUID groupId,
        String groupName,
        UUID flowId,
        String flowName,
        StudentStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy
) {
    @Component
    public static class Mapper implements IMapping<Student, StudentResponse> {
        @Override
        public StudentResponse mapping(Student from) {
            if (from == null) {
                return null;
            }
            UUID groupId = null;
            String groupName = null;
            UUID flowId = null;
            String flowName = null;
            
            if (from.getAcademicGroup() != null) {
                groupId = from.getAcademicGroup().getId();
                groupName = from.getAcademicGroup().getName();
                if (from.getAcademicGroup().getAcademicFlow() != null) {
                    flowId = from.getAcademicGroup().getAcademicFlow().getId();
                    flowName = from.getAcademicGroup().getAcademicFlow().getName();
                }
            }
            
            return new StudentResponse(
                    from.getId(),
                    from.getFirstName(),
                    from.getLastName(),
                    from.getMiddleName(),
                    from.getStudentCode(),
                    from.getUserId(),
                    groupId,
                    groupName,
                    flowId,
                    flowName,
                    from.getStatus(),
                    from.getCreatedAt(),
                    from.getUpdatedAt(),
                    from.getCreatedBy()
            );
        }
    }
}
