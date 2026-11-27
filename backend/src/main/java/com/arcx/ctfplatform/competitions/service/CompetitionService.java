package com.arcx.ctfplatform.competitions.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.competitions.dto.CompetitionRequest;
import com.arcx.ctfplatform.competitions.dto.CompetitionResponse;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;

@Service
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final IMapping<Competition, CompetitionResponse> competitionMapper;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;

    @Autowired(required = false)
    private AttemptRepository attemptRepository;

    public void setAttemptRepository(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public CompetitionService(
            CompetitionRepository competitionRepository,
            CtfTaskRepository ctfTaskRepository,
            IMapping<Competition, CompetitionResponse> competitionMapper
    ) {
        this(competitionRepository, ctfTaskRepository, competitionMapper, null);
    }

    @Autowired
    public CompetitionService(
            CompetitionRepository competitionRepository,
            CtfTaskRepository ctfTaskRepository,
            IMapping<Competition, CompetitionResponse> competitionMapper,
            @Autowired(required = false) ScoreAdjustmentRepository scoreAdjustmentRepository
    ) {
        this.competitionRepository = competitionRepository;
        this.ctfTaskRepository = ctfTaskRepository;
        this.competitionMapper = competitionMapper;
        this.scoreAdjustmentRepository = scoreAdjustmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getCompetitionsForUser(User user) {
        if (isAdminOrInstructor(user)) {
            return enrichCompetitionsWithSolveStats(competitionMapper.mappingList(competitionRepository.findAll()));
        }
        List<Competition> comps = competitionRepository.findAllByStatus(CompetitionStatus.ACTIVE);
        if (comps.isEmpty()) {
            comps = competitionRepository.findAll().stream()
                    .filter(c -> c.getStatus() != CompetitionStatus.DRAFT)
                    .toList();
        } else {
            comps = comps.stream()
                    .filter(c -> c.getStatus() != CompetitionStatus.DRAFT)
                    .toList();
        }
        List<CompetitionResponse> sanitized = competitionMapper.mappingList(comps).stream()
                .map(this::sanitizeForStudent)
                .toList();
        return enrichCompetitionsWithSolveStats(sanitized);
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getAllCompetitionsForAdmin() {
        return enrichCompetitionsWithSolveStats(competitionMapper.mappingList(competitionRepository.findAll()));
    }

    @Transactional(readOnly = true)
    public CompetitionResponse getCompetitionById(UUID id) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        CompetitionResponse mapped = competitionMapper.mapping(competition);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !isAdminOrInstructor(null)) {
            if (competition.getStatus() == CompetitionStatus.DRAFT) {
                throw new AccessDeniedException("Competition is not available");
            }
            return enrichCompetitionWithSolveStats(sanitizeForStudent(mapped));
        }
        return enrichCompetitionWithSolveStats(mapped);
    }

    @Transactional(readOnly = true)
    public CompetitionResponse getCompetitionByIdForUser(UUID id, User user) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        CompetitionResponse mapped = competitionMapper.mapping(competition);
        if (!isAdminOrInstructor(user)) {
            if (competition.getStatus() == CompetitionStatus.DRAFT) {
                throw new AccessDeniedException("Competition is not available");
            }
            return enrichCompetitionWithSolveStats(sanitizeForStudent(mapped));
        }
        return enrichCompetitionWithSolveStats(mapped);
    }

    private CompetitionResponse enrichCompetitionWithSolveStats(CompetitionResponse response) {
        if (response == null) {
            return null;
        }
        List<CompetitionResponse> enriched = enrichCompetitionsWithSolveStats(List.of(response));
        return enriched.isEmpty() ? response : enriched.get(0);
    }

    private List<CompetitionResponse> enrichCompetitionsWithSolveStats(List<CompetitionResponse> responses) {
        if (responses == null || responses.isEmpty() || attemptRepository == null) {
            return responses;
        }
        List<UUID> taskIds = responses.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(c -> c.tasks() != null ? c.tasks().stream() : java.util.stream.Stream.empty())
                .filter(java.util.Objects::nonNull)
                .map(CtfTaskResponse::id)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return responses;
        }
        java.util.Map<UUID, Long> solvedCounts = new java.util.HashMap<>();
        List<Object[]> rows = attemptRepository.countCorrectAttemptsByTaskIdIn(taskIds);
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID tid && row[1] instanceof Number cnt) {
                    solvedCounts.put(tid, cnt.longValue());
                }
            }
        }
        return responses.stream()
                .map(comp -> {
                    if (comp == null || comp.tasks() == null || comp.tasks().isEmpty()) {
                        return comp;
                    }
                    List<CtfTaskResponse> enrichedTasks = comp.tasks().stream()
                            .map(t -> t == null || t.id() == null
                                    ? t
                                    : t.withSolveStats(solvedCounts.getOrDefault(t.id(), 0L)))
                            .toList();
                    return new CompetitionResponse(
                            comp.id(),
                            comp.title(),
                            comp.description(),
                            comp.startDate(),
                            comp.endDate(),
                            comp.status(),
                            comp.createdAt(),
                            comp.sumTestPoints(),
                            comp.leaderboardHidden(),
                            comp.hiddenStudentIds(),
                            enrichedTasks
                    );
                })
                .toList();
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
        if (scoreAdjustmentRepository != null) {
            scoreAdjustmentRepository.unlinkByCompetitionId(id);
        }
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

    private boolean isAdminOrInstructor(User user) {
        if (user != null) {
            return user.getRole() == Role.ADMIN || user.getRole() == Role.INSTRUCTOR;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        if (auth.getPrincipal() instanceof User u) {
            return u.getRole() == Role.ADMIN || u.getRole() == Role.INSTRUCTOR;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ADMIN".equals(r)
                    || "ROLE_INSTRUCTOR".equals(r) || "INSTRUCTOR".equals(r)
                    || "ROLE_TEACHER".equals(r) || "TEACHER".equals(r);
        });
    }

    private CompetitionResponse sanitizeForStudent(CompetitionResponse resp) {
        if (resp == null) {
            return null;
        }
        boolean notStartedYet = resp.startDate() != null && java.time.Instant.now().isBefore(resp.startDate());
        List<com.arcx.ctfplatform.challenges.dto.CtfTaskResponse> visibleTasks = (!notStartedYet && resp.tasks() != null)
                ? resp.tasks().stream()
                    .filter(t -> t != null && !"RESERVE".equalsIgnoreCase(t.pool()))
                    .toList()
                : Collections.emptyList();
        return new CompetitionResponse(
                resp.id(),
                resp.title(),
                resp.description(),
                resp.startDate(),
                resp.endDate(),
                resp.status(),
                resp.createdAt(),
                resp.sumTestPoints(),
                resp.leaderboardHidden(),
                Collections.emptyList(),
                visibleTasks
        );
    }

    private void validateDates(java.time.Instant startDate, java.time.Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
    }
}
