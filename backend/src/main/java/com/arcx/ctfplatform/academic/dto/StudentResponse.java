package com.arcx.ctfplatform.academic.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.users.repository.UserRepository;

public record StudentResponse(
        UUID id,
        String firstName,
        String lastName,
        String middleName,
        String studentCode,
        UUID userId,
        String username,
        String email,
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
        private final UserRepository userRepository;

        public Mapper(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

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

            String username = null;
            String email = null;
            if (from.getUserId() != null) {
                var userOpt = userRepository.findById(from.getUserId());
                if (userOpt.isPresent()) {
                    username = userOpt.get().getUsername();
                    email = userOpt.get().getEmail();
                }
            }
            
            return new StudentResponse(
                    from.getId(),
                    from.getFirstName(),
                    from.getLastName(),
                    from.getMiddleName(),
                    from.getStudentCode(),
                    from.getUserId(),
                    username,
                    email,
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

