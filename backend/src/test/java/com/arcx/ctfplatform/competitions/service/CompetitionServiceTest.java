package com.arcx.ctfplatform.competitions.service;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.competitions.dto.CompetitionResponse;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;
    @Mock
    private CtfTaskRepository ctfTaskRepository;

    private CompetitionService competitionService;

    @BeforeEach
    void setUp() {
        competitionService = new CompetitionService(
                competitionRepository,
                ctfTaskRepository,
                new CompetitionResponse.Mapper()
        );
    }

    @Test
    @DisplayName("Students do not see tasks of ACTIVE competition before startDate")
    void testHideTasksBeforeStartDateForStudent() {
        UUID compId = UUID.randomUUID();
        CtfTask mainTask = new CtfTask();
        mainTask.setId(UUID.randomUUID());
        mainTask.setTitle("Secret Task");
        mainTask.setDescription("Secret Description");
        mainTask.setPool("MAIN");

        Competition upcoming = Competition.builder()
                .id(compId)
                .title("Future CTF")
                .status(CompetitionStatus.ACTIVE)
                .startDate(Instant.now().plus(2, ChronoUnit.HOURS))
                .endDate(Instant.now().plus(5, ChronoUnit.HOURS))
                .tasks(new LinkedHashSet<>(Set.of(mainTask)))
                .build();

        User student = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
        User admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();

        when(competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE)).thenReturn(List.of(upcoming));
        when(competitionRepository.findById(compId)).thenReturn(Optional.of(upcoming));

        List<CompetitionResponse> studentList = competitionService.getCompetitionsForUser(student);
        assertEquals(1, studentList.size());
        assertTrue(studentList.get(0).tasks().isEmpty());

        CompetitionResponse studentById = competitionService.getCompetitionByIdForUser(compId, student);
        assertTrue(studentById.tasks().isEmpty());

        CompetitionResponse adminById = competitionService.getCompetitionByIdForUser(compId, admin);
        assertEquals(1, adminById.tasks().size());
    }

    @Test
    @DisplayName("Students see non-RESERVE tasks once competition startDate has passed")
    void testShowNonReserveTasksAfterStartDateForStudent() {
        UUID compId = UUID.randomUUID();
        CtfTask mainTask = new CtfTask();
        mainTask.setId(UUID.randomUUID());
        mainTask.setTitle("Visible Task");
        mainTask.setPool("MAIN");

        CtfTask reserveTask = new CtfTask();
        reserveTask.setId(UUID.randomUUID());
        reserveTask.setTitle("Hidden Reserve Task");
        reserveTask.setPool("RESERVE");

        Competition started = Competition.builder()
                .id(compId)
                .title("Live CTF")
                .status(CompetitionStatus.ACTIVE)
                .startDate(Instant.now().minus(1, ChronoUnit.HOURS))
                .endDate(Instant.now().plus(2, ChronoUnit.HOURS))
                .tasks(new LinkedHashSet<>(List.of(mainTask, reserveTask)))
                .build();

        User student = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
        when(competitionRepository.findById(compId)).thenReturn(Optional.of(started));

        CompetitionResponse resp = competitionService.getCompetitionByIdForUser(compId, student);
        assertEquals(1, resp.tasks().size());
        assertEquals(mainTask.getId(), resp.tasks().get(0).id());
    }
}
