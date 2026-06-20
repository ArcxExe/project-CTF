package com.arcx.ctfplatform.challenges.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.challenges.dto.ChallengeRequest;
import com.arcx.ctfplatform.challenges.dto.ChallengeResponse;
import com.arcx.ctfplatform.challenges.entity.Challenge;
import com.arcx.ctfplatform.challenges.repository.ChallengeRepository;
import com.arcx.ctfplatform.common.config.IMapping;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final IMapping<Challenge, ChallengeResponse> challengeMapper;

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getAllChallenges() {
        return challengeMapper.mappingList(challengeRepository.findAll());
    }

    @Transactional(readOnly = true)
    public ChallengeResponse getChallengeById(UUID id) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
        return challengeMapper.mapping(challenge);
    }

    @Transactional
    public ChallengeResponse createChallenge(ChallengeRequest request) {
        Challenge challenge = new Challenge();
        challenge.setTitle(request.title());
        challenge.setDescription(request.description());
        challenge.setPoints(request.points());
        challenge.setFlag(request.flag());
        challenge.setCompetitionId(request.competitionId());

        Challenge savedChallenge = challengeRepository.save(challenge);
        return challengeMapper.mapping(savedChallenge);
    }

    @Transactional
    public ChallengeResponse updateChallenge(UUID id, ChallengeRequest request) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        challenge.setTitle(request.title());
        challenge.setDescription(request.description());
        challenge.setPoints(request.points());
        challenge.setFlag(request.flag());
        challenge.setCompetitionId(request.competitionId());

        Challenge updatedChallenge = challengeRepository.save(challenge);
        return challengeMapper.mapping(updatedChallenge);
    }

    @Transactional
    public void deleteChallenge(UUID id) {
        challengeRepository.deleteById(id);
    }
}
