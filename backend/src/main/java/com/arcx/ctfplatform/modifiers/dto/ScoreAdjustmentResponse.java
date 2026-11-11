package com.arcx.ctfplatform.modifiers.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

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
        private final StudentRepository studentRepository;
        private final UserRepository userRepository;

        public Mapper(StudentRepository studentRepository,
                      UserRepository userRepository) {
            this.studentRepository = studentRepository;
            this.userRepository = userRepository;
        }

        @Override
        public ScoreAdjustmentResponse mapping(ScoreAdjustment from) {
            if (from == null) {
                return null;
            }
            Student student = from.getStudentId() != null
                    ? studentRepository.findById(from.getStudentId()).orElse(null)
                    : null;
            User user = (student != null && student.getUserId() != null)
                    ? userRepository.findById(student.getUserId()).orElse(null)
                    : null;
            return toResponse(from, student, user);
        }

        @Override
        public List<ScoreAdjustmentResponse> mappingList(List<ScoreAdjustment> from) {
            if (from == null || from.isEmpty()) {
                return Collections.emptyList();
            }
            Set<UUID> studentIds = from.stream()
                    .filter(Objects::nonNull)
                    .map(ScoreAdjustment::getStudentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, Student> studentMap = studentIds.isEmpty()
                    ? Collections.emptyMap()
                    : studentRepository.findAllById(studentIds).stream()
                            .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));

            Set<UUID> userIds = studentMap.values().stream()
                    .map(Student::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, User> userMap = userIds.isEmpty()
                    ? Collections.emptyMap()
                    : userRepository.findAllById(userIds).stream()
                            .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

            return from.stream()
                    .filter(Objects::nonNull)
                    .map(adj -> {
                        Student student = adj.getStudentId() != null ? studentMap.get(adj.getStudentId()) : null;
                        User user = (student != null && student.getUserId() != null) ? userMap.get(student.getUserId()) : null;
                        return toResponse(adj, student, user);
                    })
                    .collect(Collectors.toList());
        }

        private ScoreAdjustmentResponse toResponse(ScoreAdjustment from, Student student, User user) {
            String studentName = "Неизвестно";
            String username = "Неизвестно";
            if (student != null) {
                String full = student.getFullName();
                studentName = (full != null && !full.isBlank()) ? full : "Неизвестно";
                if (user != null) {
                    username = user.getUsername() != null ? user.getUsername() : user.getEmail();
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
