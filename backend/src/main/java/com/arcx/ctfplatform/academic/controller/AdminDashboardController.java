package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.audit.controller.AuditLogController.AuditLogDto;
import com.arcx.ctfplatform.audit.entity.AuditLog;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final StudentRepository studentRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final AttemptRepository attemptRepository;
    private final CompetitionRepository competitionRepository;
    private final TestRepository testRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public record DashboardResponse(
            long registeredStudents,
            long publishedChallenges,
            long solvedChallenges,
            long attemptsCount,
            long activeCompetitionsCount,
            long publishedTasksCount,
            long publishedTestsCount,
            long activePromoCodesCount,
            long totalUsersCount,
            List<AuditLogDto> recentAuditLogs
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<DashboardResponse> getDashboard() {
        long registeredStudents = studentRepository.count();
        long publishedChallenges = ctfTaskRepository.count();
        List<Attempt> allAttempts = attemptRepository.findAll();
        long attemptsCount = allAttempts.size();
        long solvedChallenges = allAttempts.stream()
                .filter(Attempt::isCorrect)
                .map(Attempt::getTaskId)
                .distinct()
                .count();
        long activeCompetitionsCount = competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE).size();
        long publishedTestsCount = testRepository.findAllByStatus(TestStatus.PUBLISHED).size();
        long activePromoCodesCount = promoCodeRepository.findAll().stream()
                .filter(p -> p.getUsedCount() < p.getMaxUses())
                .count();
        long totalUsersCount = userRepository.count();
        List<AuditLogDto> recentAuditLogs = auditLogRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(AuditLogDto::from)
                .toList();

        return ResponseEntity.ok(new DashboardResponse(
                registeredStudents,
                publishedChallenges,
                solvedChallenges,
                attemptsCount,
                activeCompetitionsCount,
                publishedChallenges,
                publishedTestsCount,
                activePromoCodesCount,
                totalUsersCount,
                recentAuditLogs
        ));
    }
}
