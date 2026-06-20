package com.arcx.ctfplatform.competitions.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final IMapping<Competition, CompetitionResponse> competitionMapper;

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getCompetitionsForUser(User user) {
        if (user.getRole() == Role.ADMIN) {
            return competitionMapper.mappingList(competitionRepository.findAll());
        }
        return competitionMapper.mappingList(competitionRepository.findAllByStatus(CompetitionStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public CompetitionResponse getCompetitionById(UUID id) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));
        return competitionMapper.mapping(competition);
    }

    @Transactional
    public CompetitionResponse createCompetition(CompetitionRequest request) {
        validateDates(request.startsAt(), request.endsAt());

        Competition competition = Competition.builder()
                .title(request.title())
                .description(request.description())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .status(request.status())
                .build();

        Competition saved = competitionRepository.save(competition);
        return competitionMapper.mapping(saved);
    }

    @Transactional
    public CompetitionResponse updateCompetition(UUID id, CompetitionRequest request) {
        Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));

        validateDates(request.startsAt(), request.endsAt());

        competition.setTitle(request.title());
        competition.setDescription(request.description());
        competition.setStartsAt(request.startsAt());
        competition.setEndsAt(request.endsAt());
        competition.setStatus(request.status());

        Competition updated = competitionRepository.save(competition);
        return competitionMapper.mapping(updated);
    }

    @Transactional
    public void deleteCompetition(UUID id) {
        competitionRepository.deleteById(id);
    }

    private void validateDates(java.time.Instant startsAt, java.time.Instant endsAt) {
        if (startsAt != null && endsAt != null && startsAt.isAfter(endsAt)) {
            throw new IllegalArgumentException("startsAt cannot be after endsAt");
        }
    }
}
