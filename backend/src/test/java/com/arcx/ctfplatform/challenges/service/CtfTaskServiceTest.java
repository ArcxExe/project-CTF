package com.arcx.ctfplatform.challenges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtfTaskServiceTest {

    @Mock
    private CtfTaskRepository ctfTaskRepository;
    @Mock
    private AttemptRepository attemptRepository;

    private CtfTaskService ctfTaskService;

    @BeforeEach
    void setUp() {
        ctfTaskService = new CtfTaskService(ctfTaskRepository, new CtfTaskResponse.Mapper());
        ctfTaskService.setAttemptRepository(attemptRepository);
    }

    @Test
    @DisplayName("Issue #32: getTaskById populates solvedCount and firstBloodTaken")
    void getTaskByIdPopulatesSolveStats() {
        UUID taskId = UUID.randomUUID();
        CtfTask task = new CtfTask();
        task.setId(taskId);
        task.setTitle("First Blood Challenge");
        task.setFirstBloodOnly(true);
        task.setBaseScore(300);

        when(ctfTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(attemptRepository.countByTaskIdAndIsCorrect(taskId, true)).thenReturn(1L);

        CtfTaskResponse response = ctfTaskService.getTaskById(taskId);
        assertThat(response.isFirstBloodOnly()).isTrue();
        assertThat(response.solvedCount()).isEqualTo(1L);
        assertThat(response.firstBloodTaken()).isTrue();
    }

    @Test
    @DisplayName("Issue #32: getAllTasks populates solvedCount and firstBloodTaken in a single batch query")
    void getAllTasksPopulatesSolveStatsInBatch() {
        UUID solvedId = UUID.randomUUID();
        UUID unsolvedId = UUID.randomUUID();

        CtfTask solved = new CtfTask();
        solved.setId(solvedId);
        solved.setTitle("Solved FB");
        solved.setFirstBloodOnly(true);

        CtfTask unsolved = new CtfTask();
        unsolved.setId(unsolvedId);
        unsolved.setTitle("Unsolved FB");
        unsolved.setFirstBloodOnly(true);

        when(ctfTaskRepository.findAll()).thenReturn(List.of(solved, unsolved));
        when(attemptRepository.countCorrectAttemptsByTaskIdIn(List.of(solvedId, unsolvedId)))
                .thenReturn(List.<Object[]>of(new Object[] { solvedId, 3L }));

        List<CtfTaskResponse> responses = ctfTaskService.getAllTasks();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).solvedCount()).isEqualTo(3L);
        assertThat(responses.get(0).firstBloodTaken()).isTrue();
        assertThat(responses.get(1).solvedCount()).isZero();
        assertThat(responses.get(1).firstBloodTaken()).isFalse();
    }
}
