package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;

public record ScoreAdjustmentResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String username,
        UUID competitionId,
        Integer points,
        String reason,
        Instant createdAt
) {
    @Component
    public static class Mapper implements IMapping<ScoreAdjustment, ScoreAdjustmentResponse> {
        private final com.arcx.ctfplatform.academic.repository.StudentRepository studentRepository;
        private final com.arcx.ctfplatform.users.repository.UserRepository userRepository;

        public Mapper(com.arcx.ctfplatform.academic.repository.StudentRepository studentRepository,
                      com.arcx.ctfplatform.users.repository.UserRepository userRepository) {
            this.studentRepository = studentRepository;
            this.userRepository = userRepository;
        }

        @Override
        public ScoreAdjustmentResponse mapping(ScoreAdjustment from) {
            if (from == null) {
                return null;
            }
            String studentName = "Неизвестно";
            String username = "Неизвестно";
            if (from.getStudentId() != null) {
                var studentOpt = studentRepository.findById(from.getStudentId());
                if (studentOpt.isPresent()) {
                    var student = studentOpt.get();
                    studentName = student.getFirstName() + " " + student.getLastName();
                    if (student.getUserId() != null) {
                        var userOpt = userRepository.findById(student.getUserId());
                        if (userOpt.isPresent()) {
                            var u = userOpt.get();
                            username = u.getUsername() != null ? u.getUsername() : u.getEmail();
                        }
                    }
                }
            }
            return new ScoreAdjustmentResponse(
                    from.getId(),
                    from.getStudentId(),
                    studentName,
                    username,
                    from.getCompetitionId(),
                    from.getPoints(),
                    from.getReason(),
                    from.getCreatedAt()
            );
        }
    }
}
