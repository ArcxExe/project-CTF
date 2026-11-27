package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.audit.repository.AuditLogRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CtfTaskRepository ctfTaskRepository;
    @Mock private AttemptRepository attemptRepository;
    @Mock private CompetitionRepository competitionRepository;
    @Mock private TestRepository testRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Issue #34: publishedChallenges counts only unique non-RESERVE tasks in ACTIVE competitions or PUBLISHED tests")
    void countsOnlyPublishedNonReserveLinkedTasks() {
        AdminDashboardController controller = new AdminDashboardController(
                studentRepository, ctfTaskRepository, attemptRepository, competitionRepository,
                testRepository, promoCodeRepository, userRepository, auditLogRepository
        );

        CtfTask mainInBoth = new CtfTask();
        mainInBoth.setId(UUID.randomUUID());
        mainInBoth.setPool("MAIN");

        CtfTask mainInTest = new CtfTask();
        mainInTest.setId(UUID.randomUUID());
        mainInTest.setPool("MAIN");

        CtfTask reserveTask = new CtfTask();
        reserveTask.setId(UUID.randomUUID());
        reserveTask.setPool("RESERVE");

        Competition activeComp = Competition.builder()
                .id(UUID.randomUUID())
                .status(CompetitionStatus.ACTIVE)
                .tasks(new LinkedHashSet<>(Set.of(mainInBoth, reserveTask)))
                .build();

        com.arcx.ctfplatform.tests.entity.Test publishedTest = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(UUID.randomUUID())
                .status(TestStatus.PUBLISHED)
                .tasks(List.of(mainInBoth, mainInTest))
                .build();

        when(studentRepository.count()).thenReturn(10L);
        when(competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE)).thenReturn(List.of(activeComp));
        when(testRepository.findAllByStatus(TestStatus.PUBLISHED)).thenReturn(List.of(publishedTest));
        when(attemptRepository.findAll()).thenReturn(List.of());
        when(promoCodeRepository.findAll()).thenReturn(List.of());
        when(userRepository.count()).thenReturn(12L);
        when(auditLogRepository.findAll()).thenReturn(List.of());

        ResponseEntity<AdminDashboardController.DashboardResponse> resp = controller.getDashboard();

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().publishedChallenges()).isEqualTo(2L);
        assertThat(resp.getBody().publishedTasksCount()).isEqualTo(2L);
    }
}
