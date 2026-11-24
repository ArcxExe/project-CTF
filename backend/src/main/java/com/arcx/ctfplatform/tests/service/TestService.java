package com.arcx.ctfplatform.tests.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.service.AttemptService;
import com.arcx.ctfplatform.challenges.dto.CtfTaskResponse;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.config.IMapping;
import com.arcx.ctfplatform.tests.dto.TestRequest;
import com.arcx.ctfplatform.tests.dto.TestResponse;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.entity.TestType;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.Role;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final AttemptService attemptService;
    private final IMapping<Test, TestResponse> testMapper;
    private final IMapping<CtfTask, CtfTaskResponse> ctfTaskMapper;
    private final LeaderboardService leaderboardService;

    @Autowired(required = false)
    private QuizQuestionRepository quizQuestionRepository;

    @Transactional(readOnly = true)
    public List<TestResponse> getPublishedTests() {
        return mapTests(testRepository.findAllByStatus(TestStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<CtfTaskResponse> getChallengesForTest(UUID testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean studentRequest = auth != null && !isAdminOrInstructor(null);
        if (studentRequest && test.getStatus() != TestStatus.PUBLISHED) {
            throw new AccessDeniedException("Test is not published");
        }
        List<CtfTask> tasks = test.getTasks() != null ? test.getTasks() : List.of();
        if (studentRequest) {
            tasks = filterVisibleTasksForStudent(tasks);
        }
        return ctfTaskMapper.mappingList(tasks);
    }

    @Transactional(readOnly = true)
    public List<CtfTaskResponse> getChallengesForTestForCaller(UUID testId, User user) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        boolean staff = isAdminOrInstructor(user);
        if (!staff && test.getStatus() != TestStatus.PUBLISHED) {
            throw new AccessDeniedException("Test is not published");
        }
        List<CtfTask> tasks = test.getTasks() != null ? test.getTasks() : List.of();
        if (!staff) {
            tasks = filterVisibleTasksForStudent(tasks);
        }
        return ctfTaskMapper.mappingList(tasks);
    }

    @Transactional
    public ChallengeSubmitResponse submitChallengeFlag(UUID testId, UUID challengeId, String flag, User user) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new IllegalStateException("Test is not published");
        }

        CtfTask task = test.getTasks().stream()
                .filter(c -> c != null && challengeId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found in this test"));
        if ("RESERVE".equalsIgnoreCase(task.getPool())) {
            throw new IllegalArgumentException("Task is not available");
        }

        ChallengeSubmitResponse response = attemptService.submitFlag(user.getId(), challengeId, flag);
        if (response.isCorrect()) {
            leaderboardService.broadcastUpdate();
        }
        return response;
    }

    private List<CtfTask> filterVisibleTasksForStudent(List<CtfTask> tasks) {
        return tasks.stream()
                .filter(t -> t != null && !"RESERVE".equalsIgnoreCase(t.getPool()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestResponse> getAllTests() {
        return mapTests(testRepository.findAll());
    }

    @Transactional(readOnly = true)
    public TestResponse getTestById(UUID id) {
        return mapTest(testRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Test not found")));
    }

    @Transactional
    public TestResponse createTest(TestRequest request) {
        TestType type = determineTestType(request.type(), request.title());
        Test test = Test.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status())
                .timeLimitMinutes(request.timeLimitMinutes())
                .passingScore(request.passingScore() != null ? request.passingScore() : 0)
                .questionsCount(request.questionsCount() != null ? request.questionsCount() : 0)
                .competitionId(request.competitionId())
                .type(type)
                .ktNumber(type != null && type.isControlPoint() ? request.ktNumber() : null)
                .build();
        return mapTest(testRepository.save(test));
    }

    @Transactional
    public TestResponse updateTest(UUID id, TestRequest request) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        test.setTitle(request.title());
        test.setDescription(request.description());
        test.setStatus(request.status());
        test.setTimeLimitMinutes(request.timeLimitMinutes());
        if (request.passingScore() != null) {
            test.setPassingScore(request.passingScore());
        }
        if (request.questionsCount() != null) {
            test.setQuestionsCount(request.questionsCount());
        }
        test.setCompetitionId(request.competitionId());
        if (request.type() != null) {
            test.setType(request.type());
        } else if (test.getType() == null) {
            test.setType(determineTestType(null, request.title() != null ? request.title() : test.getTitle()));
        }
        if (test.getType() != null && test.getType().isControlPoint()) {
            if (request.ktNumber() != null) {
                test.setKtNumber(request.ktNumber());
            }
        } else {
            test.setKtNumber(null);
        }

        return mapTest(testRepository.save(test));
    }

    private List<TestResponse> mapTests(List<Test> tests) {
        if (tests == null || tests.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> ids = tests.stream().map(Test::getId).toList();
        List<QuizQuestion> questions = quizQuestionRepository != null
                ? quizQuestionRepository.findAllByTestIdIn(ids)
                : Collections.emptyList();
        Map<UUID, Integer> counts = KtScoreCalculator.computeQuestionCounts(questions);
        Map<UUID, Integer> maxScores = KtScoreCalculator.computeQuestionMaxScores(questions);
        return tests.stream()
                .map(t -> toTestResponse(t, counts.getOrDefault(t.getId(), 0), maxScores.getOrDefault(t.getId(), 0)))
                .toList();
    }

    private TestResponse mapTest(Test test) {
        if (test == null) {
            return null;
        }
        List<QuizQuestion> questions = (quizQuestionRepository != null && test.getId() != null)
                ? quizQuestionRepository.findAllByTestIdOrderByOrderingAsc(test.getId())
                : Collections.emptyList();
        int count = questions.size();
        int maxScore = questions.stream()
                .mapToInt(q -> q.getPoints() != null ? Math.max(0, q.getPoints()) : 0)
                .sum();
        return toTestResponse(test, count, maxScore);
    }

    private TestResponse toTestResponse(Test from, int actualQuestionCount, int maxScore) {
        TestResponse base = testMapper.mapping(from);
        return new TestResponse(
                base.id(),
                base.title(),
                base.description(),
                base.status(),
                base.timeLimitMinutes(),
                base.passingScore(),
                actualQuestionCount,
                base.competitionId(),
                base.createdAt(),
                base.updatedAt(),
                base.type(),
                base.ktNumber(),
                maxScore
        );
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

    private TestType determineTestType(TestType explicitType, String title) {
        if (explicitType != null) {
            return explicitType;
        }
        if (title != null && (title.toUpperCase().contains("КТ") || title.toLowerCase().contains("контрольн"))) {
            return TestType.CONTROL_POINT;
        }
        return TestType.LECTURE;
    }

    @Transactional
    public void deleteTest(UUID id) {
        testRepository.deleteQuizAttemptsByTestId(id);
        testRepository.deleteById(id);
    }

    @Transactional
    public void addChallengeToTest(UUID testId, UUID challengeId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        CtfTask task = ctfTaskRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        if (!test.getTasks().contains(task)) {
            test.getTasks().add(task);
            testRepository.save(test);
        }
    }

    @Transactional
    public void removeChallengeFromTest(UUID testId, UUID challengeId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        CtfTask task = ctfTaskRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        test.getTasks().remove(task);
        testRepository.save(test);
    }
}
