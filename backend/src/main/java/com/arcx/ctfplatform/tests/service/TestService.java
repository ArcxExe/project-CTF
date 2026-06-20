package com.arcx.ctfplatform.tests.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.challenges.dto.ChallengeResponse;
import com.arcx.ctfplatform.challenges.entity.Challenge;
import com.arcx.ctfplatform.challenges.repository.ChallengeRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.tests.dto.TestRequest;
import com.arcx.ctfplatform.tests.dto.TestResponse;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final ChallengeRepository challengeRepository;
    private final AttemptService attemptService;
    private final IMapping<Test, TestResponse> testMapper;
    private final IMapping<Challenge, ChallengeResponse> challengeMapper;
    private final LeaderboardService leaderboardService;

    @Transactional(readOnly = true)
    public List<TestResponse> getPublishedTests() {
        return testMapper.mappingList(testRepository.findAllByStatus(TestStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getChallengesForTest(UUID testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        return challengeMapper.mappingList(test.getChallenges());
    }

    @Transactional
    public ChallengeSubmitResponse submitChallengeFlag(UUID testId, UUID challengeId, String flag, User user) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        boolean hasChallenge = test.getChallenges().stream().anyMatch(c -> c.getId().equals(challengeId));
        if (!hasChallenge) {
            throw new IllegalArgumentException("Challenge not found in this test");
        }

        ChallengeSubmitResponse response = attemptService.submitFlag(user.getId(), challengeId, flag);
        if (response.isCorrect()) {
            leaderboardService.broadcastUpdate();
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<TestResponse> getAllTests() {
        return testMapper.mappingList(testRepository.findAll());
    }

    @Transactional(readOnly = true)
    public TestResponse getTestById(UUID id) {
        return testMapper.mapping(testRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Test not found")));
    }

    @Transactional
    public TestResponse createTest(TestRequest request) {
        Test test = Test.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status())
                .timeLimitMinutes(request.timeLimitMinutes())
                .passingScore(request.passingScore())
                .questionsCount(request.questionsCount())
                .competitionId(request.competitionId())
                .build();
        return testMapper.mapping(testRepository.save(test));
    }

    @Transactional
    public TestResponse updateTest(UUID id, TestRequest request) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        test.setTitle(request.title());
        test.setDescription(request.description());
        test.setStatus(request.status());
        test.setTimeLimitMinutes(request.timeLimitMinutes());
        test.setPassingScore(request.passingScore());
        test.setQuestionsCount(request.questionsCount());
        test.setCompetitionId(request.competitionId());

        return testMapper.mapping(testRepository.save(test));
    }

    @Transactional
    public void deleteTest(UUID id) {
        testRepository.deleteById(id);
    }

    @Transactional
    public void addChallengeToTest(UUID testId, UUID challengeId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        if (!test.getChallenges().contains(challenge)) {
            test.getChallenges().add(challenge);
            testRepository.save(test);
        }
    }

    @Transactional
    public void removeChallengeFromTest(UUID testId, UUID challengeId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        test.getChallenges().remove(challenge);
        testRepository.save(test);
    }
}
