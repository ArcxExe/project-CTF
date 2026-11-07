package com.arcx.ctfplatform.academic.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.attendance.repository.StudentAttendanceRepository;
import com.arcx.ctfplatform.academic.dto.ResetMode;
import com.arcx.ctfplatform.academic.dto.ResetRequestDto;
import com.arcx.ctfplatform.academic.dto.ResetResultDto;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicFlowRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.audit.service.AuditService;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AcademicResetService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AcademicGroupRepository groupRepository;
    private final AcademicFlowRepository flowRepository;
    private final AttemptRepository attemptRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final StudentLabRepository studentLabRepository;
    private final LabScoreRepository labScoreRepository;
    private final LabScoreHistoryRepository labScoreHistoryRepository;
    private final StudentAttendanceRepository attendanceRepository;
    private final AuditService auditService;
    private final LeaderboardService leaderboardService;

    @PersistenceContext
    private EntityManager entityManager;

    public AcademicResetService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            AcademicGroupRepository groupRepository,
            AcademicFlowRepository flowRepository,
            AttemptRepository attemptRepository,
            QuizAttemptRepository quizAttemptRepository,
            PromoCodeClaimRepository promoCodeClaimRepository,
            PromoCodeRepository promoCodeRepository,
            ScoreAdjustmentRepository scoreAdjustmentRepository,
            StudentLabRepository studentLabRepository,
            LabScoreRepository labScoreRepository,
            LabScoreHistoryRepository labScoreHistoryRepository,
            StudentAttendanceRepository attendanceRepository,
            AuditService auditService,
            LeaderboardService leaderboardService,
            EntityManager entityManager) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.flowRepository = flowRepository;
        this.attemptRepository = attemptRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.promoCodeClaimRepository = promoCodeClaimRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.scoreAdjustmentRepository = scoreAdjustmentRepository;
        this.studentLabRepository = studentLabRepository;
        this.labScoreRepository = labScoreRepository;
        this.labScoreHistoryRepository = labScoreHistoryRepository;
        this.attendanceRepository = attendanceRepository;
        this.auditService = auditService;
        this.leaderboardService = leaderboardService;
        this.entityManager = entityManager;
    }

    @Transactional
    public ResetResultDto resetSemester(ResetRequestDto request, User currentUser) {
        validateConfirmationPhrase(request.getConfirmationPhrase());

        if (request.getMode() == null) {
            throw new IllegalArgumentException("Режим очистки обязателен");
        }

        UUID streamId = request.getStreamId();
        UUID groupId = request.getGroupId();

        List<Student> targetedStudents = resolveTargetedStudents(streamId, groupId);
        List<UUID> studentIds = targetedStudents.stream().map(Student::getId).toList();
        boolean isPlatformWide = (streamId == null && groupId == null);

        // 1. Wipe progress records
        int deletedAttempts = 0;
        int deletedQuizAttempts = 0;
        int deletedLabRecords = 0;
        int deletedAttendanceRecords = 0;
        int resetControlPointRecords = 0;

        for (Student s : targetedStudents) {
            s.setKt1(0.0);
            s.setKt2(0.0);
            s.setKt3(0.0);
            s.setKt4(0.0);
            s.setTokenInfo(null);
            s.setExamScore(null);
        }

        if (isPlatformWide) {
            deletedAttempts = attemptRepository.deleteAllAttempts();
            deletedQuizAttempts = quizAttemptRepository.deleteAllQuizAttempts();
            promoCodeClaimRepository.deleteAllClaims();
            promoCodeRepository.resetAllUsedCounts();
            scoreAdjustmentRepository.deleteAllAdjustments();

            int h = labScoreHistoryRepository.deleteAllHistory();
            int sl = studentLabRepository.deleteAllStudentLabs();
            int ls = labScoreRepository.deleteAllScores();
            deletedLabRecords = h + sl + ls;

            studentRepository.deleteAllManualSubmissions();
            deletedAttendanceRecords = attendanceRepository.deleteAllAttendance();
            int updatedKt = studentRepository.resetAllGradebookFields();
            resetControlPointRecords = updatedKt > 0 ? updatedKt : targetedStudents.size();
        } else if (!studentIds.isEmpty()) {
            for (List<UUID> batch : partition(studentIds, 500)) {
                deletedAttempts += attemptRepository.deleteByStudentIdIn(batch);
                deletedQuizAttempts += quizAttemptRepository.deleteByStudentIdIn(batch);
                promoCodeClaimRepository.deleteByStudentIdIn(batch);
                scoreAdjustmentRepository.deleteByStudentIdIn(batch);

                int h = labScoreHistoryRepository.deleteByStudentIdIn(batch);
                int sl = studentLabRepository.deleteByStudentIdIn(batch);
                int ls = labScoreRepository.deleteByStudentIdIn(batch);
                deletedLabRecords += (h + sl + ls);

                studentRepository.deleteManualSubmissionsByStudentIdIn(batch);
                deletedAttendanceRecords += attendanceRepository.deleteByStudentIdIn(batch);
                int updatedKt = studentRepository.resetGradebookFieldsByStudentIdIn(batch);
                resetControlPointRecords += (updatedKt > 0 ? updatedKt : batch.size());
            }
            promoCodeRepository.recalculateUsedCounts();
        }

        // 2. Full semester wipe additions
        int deletedStudents = 0;
        int deletedUsers = 0;
        int deletedGroups = 0;

        if (request.getMode() == ResetMode.FULL_SEMESTER_WIPE) {
            Set<UUID> userIdsToDelete = targetedStudents.stream()
                    .map(Student::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (isPlatformWide) {
                studentRepository.deleteAllHiddenStudents();
                deletedStudents = studentRepository.deleteAllStudents();
            } else if (!studentIds.isEmpty()) {
                for (List<UUID> batch : partition(studentIds, 500)) {
                    studentRepository.deleteHiddenStudentsByStudentIdIn(batch);
                    deletedStudents += studentRepository.deleteStudentsByIdIn(batch);
                }
            }

            for (UUID userId : userIdsToDelete) {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!isProtectedUser(user)) {
                        userRepository.delete(user);
                        deletedUsers++;
                    } else {
                        log.warn("Preserved privileged user account {} ({}) from deletion during semester reset",
                                user.getUsername(), user.getRole());
                    }
                }
            }

            if (groupId == null) {
                if (isPlatformWide) {
                    deletedGroups = groupRepository.deleteAllGroups();
                    flowRepository.deleteAllFlows();
                } else {
                    deletedGroups = groupRepository.deleteByAcademicFlowId(streamId);
                    flowRepository.deleteById(streamId);
                }
            }
        }

        if (entityManager != null) {
            entityManager.flush();
        }

        // 3. Audit logging
        UUID adminId = null;
        String adminUsername = "ADMIN";
        if (currentUser != null) {
            adminId = currentUser.getId();
            adminUsername = currentUser.getUsername();
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User u) {
                adminId = u.getId();
                adminUsername = u.getUsername();
            } else if (auth != null) {
                adminUsername = auth.getName();
            }
        }

        String targetScope = isPlatformWide ? "PLATFORM"
                : (groupId != null ? "GROUP:" + groupId : "STREAM:" + streamId);
        String details = String.format(
                "mode=%s, deletedStudents=%d, deletedUsers=%d, deletedGroups=%d, deletedAttempts=%d, deletedQuizAttempts=%d, deletedLabRecords=%d, deletedAttendanceRecords=%d, resetControlPointRecords=%d",
                request.getMode(), deletedStudents, deletedUsers, deletedGroups, deletedAttempts, deletedQuizAttempts, deletedLabRecords, deletedAttendanceRecords, resetControlPointRecords);

        auditService.logEvent(
                adminId,
                adminUsername,
                "ADMIN",
                "ACADEMIC_SEMESTER_RESET",
                targetScope,
                details,
                "SUCCESS",
                null
        );

        // 4. SSE Leaderboard broadcast
        try {
            leaderboardService.broadcastUpdate();
        } catch (Exception e) {
            log.warn("Failed to broadcast leaderboard update after semester reset: {}", e.getMessage());
        }

        return ResetResultDto.builder()
                .deletedStudents(deletedStudents)
                .deletedUsers(deletedUsers)
                .deletedGroups(deletedGroups)
                .deletedAttempts(deletedAttempts)
                .deletedQuizAttempts(deletedQuizAttempts)
                .deletedLabRecords(deletedLabRecords)
                .deletedAttendanceRecords(deletedAttendanceRecords)
                .resetControlPointRecords(resetControlPointRecords)
                .timestamp(Instant.now())
                .build();
    }

    private void validateConfirmationPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            throw new IllegalArgumentException("Фраза подтверждения обязательна");
        }
        String trimmed = phrase.trim();
        if (!"ОЧИСТИТЬ КУРС".equalsIgnoreCase(trimmed) && !"RESET".equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Неверная фраза подтверждения. Введите 'ОЧИСТИТЬ КУРС' или 'RESET' для подтверждения действия.");
        }
    }

    private List<Student> resolveTargetedStudents(UUID streamId, UUID groupId) {
        if (groupId != null) {
            AcademicGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Группа не найдена: " + groupId));
            if (streamId != null && !streamId.equals(group.getStreamId())) {
                throw new IllegalArgumentException("Группа не принадлежит указанному потоку");
            }
            return studentRepository.findByAcademicGroupId(groupId);
        } else if (streamId != null) {
            if (!flowRepository.existsById(streamId)) {
                throw new IllegalArgumentException("Поток не найден: " + streamId);
            }
            return studentRepository.findByAcademicFlowId(streamId);
        } else {
            return studentRepository.findAll();
        }
    }

    private boolean isProtectedUser(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.INSTRUCTOR) {
            return true;
        }
        String roleName = user.getRole().name();
        if ("ADMIN".equalsIgnoreCase(roleName) || "INSTRUCTOR".equalsIgnoreCase(roleName)) {
            return true;
        }
        if (user.getAuthorities() != null && user.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority().toUpperCase();
                    return auth.contains("ADMIN") || auth.contains("INSTRUCTOR");
                })) {
            return true;
        }
        return false;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
