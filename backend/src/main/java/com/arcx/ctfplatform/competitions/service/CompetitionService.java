package com.arcx.ctfplatform.competitions.service;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.competitions.dto.CompetitionRequest;
import com.arcx.ctfplatform.competitions.dto.CompetitionResponse;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final IMapping<Competition, CompetitionResponse> competitionMapper;

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getCompetitionsForUser(User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.INSTRUCTOR) {
            return competitionMapper.mappingList(competitionRepository.findAll());
        }
        return competitionMapper.mappingList(competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getAllCompetitionsForAdmin() {
        return competitionMapper.mappingList(competitionRepository.findAll());
    }

    @Transactional(readOnly = true)
    public CompetitionResponse getCompetitionById(UUID id) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        return competitionMapper.mapping(competition);
    }

    @Transactional
    public CompetitionResponse createCompetition(CompetitionRequest request) {
        validateDates(request.startDate(), request.endDate());

        Competition competition = Competition.builder()
                .title(request.title())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status())
                .sumTestPoints(request.sumTestPoints())
                .leaderboardHidden(request.leaderboardHidden())
                .hiddenStudentIds(request.hiddenStudentIds() != null ? request.hiddenStudentIds() : new java.util.ArrayList<>())
                .build();

        Competition saved = competitionRepository.save(competition);
        return competitionMapper.mapping(saved);
    }

    @Transactional
    public CompetitionResponse updateCompetition(UUID id, CompetitionRequest request) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));

        validateDates(request.startDate(), request.endDate());

        competition.setTitle(request.title());
        competition.setDescription(request.description());
        competition.setStartDate(request.startDate());
        competition.setEndDate(request.endDate());
        competition.setStatus(request.status());
        competition.setSumTestPoints(request.sumTestPoints());
        competition.setLeaderboardHidden(request.leaderboardHidden());
        competition.setHiddenStudentIds(request.hiddenStudentIds() != null ? request.hiddenStudentIds() : new java.util.ArrayList<>());

        Competition updated = competitionRepository.save(competition);
        return competitionMapper.mapping(updated);
    }

    @Transactional
    public void deleteCompetition(UUID id) {
        competitionRepository.deleteById(id);
    }

    @Transactional
    public void linkTasksToCompetition(UUID competitionId, List<UUID> taskIds) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));

        List<CtfTask> tasks = ctfTaskRepository.findAllById(taskIds);
        competition.setTasks(new LinkedHashSet<>(tasks));
        competitionRepository.save(competition);
    }

    private void validateDates(java.time.Instant startDate, java.time.Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
    }
}
