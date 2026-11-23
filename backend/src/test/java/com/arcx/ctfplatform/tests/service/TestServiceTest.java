package com.arcx.ctfplatform.tests.service;

import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.dto.TestResponse;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {

    @Mock
    private TestRepository testRepository;
    @Mock
    private CtfTaskRepository ctfTaskRepository;
    @Mock
    private AttemptService attemptService;
    @Mock
    private IMapping<com.arcx.ctfplatform.tests.entity.Test, TestResponse> testMapper;
    @Mock
    private LeaderboardService leaderboardService;

    private TestService testService;

    @BeforeEach
    void setUp() {
        testService = new TestService(
                testRepository,
                ctfTaskRepository,
                attemptService,
                testMapper,
                new CtfTaskResponse.Mapper(),
                leaderboardService
        );
    }

    @Test
    @DisplayName("getChallengesForTestForCaller filters out RESERVE tasks for students and keeps them for staff")
    void testGetChallengesFiltersReserveForStudentsOnly() {
        UUID testId = UUID.randomUUID();
        CtfTask mainTask = new CtfTask();
        mainTask.setId(UUID.randomUUID());
        mainTask.setTitle("Main Task");
        mainTask.setPool("MAIN");

        CtfTask reserveTask = new CtfTask();
        reserveTask.setId(UUID.randomUUID());
        reserveTask.setTitle("Reserve Task");
        reserveTask.setPool("RESERVE");

        com.arcx.ctfplatform.tests.entity.Test test = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(testId)
                .title("Published Test")
                .status(TestStatus.PUBLISHED)
                .tasks(List.of(mainTask, reserveTask))
                .build();

        User student = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        List<CtfTaskResponse> studentTasks = testService.getChallengesForTestForCaller(testId, student);
        assertEquals(1, studentTasks.size());
        assertEquals(mainTask.getId(), studentTasks.get(0).id());

        List<CtfTaskResponse> adminTasks = testService.getChallengesForTestForCaller(testId, admin);
        assertEquals(2, adminTasks.size());
    }

    @Test
    @DisplayName("submitChallengeFlag rejects RESERVE task in test")
    void testSubmitChallengeFlagRejectsReserveTask() {
        UUID testId = UUID.randomUUID();
        UUID reserveTaskId = UUID.randomUUID();
        CtfTask reserveTask = new CtfTask();
        reserveTask.setId(reserveTaskId);
        reserveTask.setTitle("Reserve Task");
        reserveTask.setPool("RESERVE");

        com.arcx.ctfplatform.tests.entity.Test test = com.arcx.ctfplatform.tests.entity.Test.builder()
                .id(testId)
                .title("Published Test")
                .status(TestStatus.PUBLISHED)
                .tasks(List.of(reserveTask))
                .build();

        User student = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> testService.submitChallengeFlag(testId, reserveTaskId, "CTF{flag}", student)
        );

        assertEquals("Task is not available", ex.getMessage());
        verify(attemptService, never()).submitFlag(any(), any(), any());
    }
}
