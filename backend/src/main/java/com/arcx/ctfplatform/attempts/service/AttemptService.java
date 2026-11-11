package com.arcx.ctfplatform.attempts.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.storage.FileStorageService;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.entity.TestStatus;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttemptService {

    private static final int MAX_WRONG_ATTEMPTS_PER_MINUTE = 5;

    private final StudentRepository studentRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final AttemptRepository attemptRepository;
    private final CompetitionRepository competitionRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final TestRepository testRepository;

    public AttemptService(
            StudentRepository studentRepository,
            CtfTaskRepository ctfTaskRepository,
            AttemptRepository attemptRepository,
            CompetitionRepository competitionRepository,
            FileStorageService fileStorageService,
            UserRepository userRepository,
            LeaderboardService leaderboardService
    ) {
        this(studentRepository, ctfTaskRepository, attemptRepository, competitionRepository,
                fileStorageService, userRepository, leaderboardService, null);
    }

    @Autowired
    public AttemptService(
            StudentRepository studentRepository,
            CtfTaskRepository ctfTaskRepository,
            AttemptRepository attemptRepository,
            CompetitionRepository competitionRepository,
            FileStorageService fileStorageService,
            UserRepository userRepository,
            LeaderboardService leaderboardService,
            @Autowired(required = false) TestRepository testRepository
    ) {
        this.studentRepository = studentRepository;
        this.ctfTaskRepository = ctfTaskRepository;
        this.attemptRepository = attemptRepository;
        this.competitionRepository = competitionRepository;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.testRepository = testRepository;
    }

    @Transactional
    public ChallengeSubmitResponse submitFlag(UUID userId, UUID taskId, String submittedFlag) {
        Student student = getStudentOrThrow(userId);
        validateStudentCanSubmit(student);

        CtfTask task = getTaskOrThrow(taskId);

        if (task.getTaskType() != TaskType.FLAG) {
            throw new IllegalArgumentException("Invalid task type for this endpoint");
        }

        checkCompetitionStatusForTask(task);
        checkIfAlreadySolved(taskId, student.getId());

        long solves = attemptRepository.countByTaskIdAndIsCorrect(task.getId(), true);
        boolean anyCorrectAlready = solves > 0 || attemptRepository.existsByTaskIdAndIsCorrectTrue(task.getId());
        if (task.isFirstBloodOnly() && anyCorrectAlready) {
            throw new IllegalArgumentException("First Blood Only challenge has already been solved");
        }

        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        long recentWrongAttempts = attemptRepository.countByTaskIdAndStudentIdAndIsCorrectFalseAndSubmittedAtAfter(
                task.getId(), student.getId(), oneMinuteAgo);
        if (recentWrongAttempts >= MAX_WRONG_ATTEMPTS_PER_MINUTE) {
            throw new IllegalStateException("Too many wrong attempts. Please wait before trying again.");
        }

        boolean isCorrect = task.getFlag() != null && submittedFlag != null && task.getFlag().equals(submittedFlag);
        boolean isFirstBlood = isCorrect && !anyCorrectAlready;
        
        int firstBloodReward = (task.getMaxScore() != null && task.getMaxScore() > 0) ? task.getMaxScore() : task.getBaseScore();
        Integer score = isCorrect ? (task.isFirstBloodOnly() ? (isFirstBlood ? firstBloodReward : 0) : task.getBaseScore()) : 0;

        Attempt attempt = Attempt.builder()
                .taskId(task.getId())
                .studentId(student.getId())
                .submittedFlag(submittedFlag != null ? submittedFlag : "")
                .isCorrect(isCorrect)
                .scoreAwarded(score)
                .build();

        attemptRepository.save(attempt);

        if (isCorrect) {
            leaderboardService.broadcastUpdate();
            if (isFirstBlood && !shouldSuppressFirstBloodBroadcast(task, student.getId())) {
                String username = userRepository.findById(student.getUserId())
                    .map(User::getUsername)
                    .orElse(student.getStudentCode() != null ? student.getStudentCode() : "Student");
                leaderboardService.broadcastFirstBlood(username, task.getTitle());
            }
        }

        String message = isCorrect ? "Correct flag! You earned " + score + " points." : "Incorrect flag, try again.";
        return new ChallengeSubmitResponse(isCorrect, message);
    }

    @Transactional
    public ChallengeSubmitResponse submitFile(UUID userId, UUID taskId, MultipartFile file) {
        Student student = getStudentOrThrow(userId);
        validateStudentCanSubmit(student);

        CtfTask task = getTaskOrThrow(taskId);

        if (task.getTaskType() != TaskType.FILE_UPLOAD) {
            throw new IllegalArgumentException("Invalid task type for file upload");
        }

        checkCompetitionStatusForTask(task);
        checkIfAlreadySolved(taskId, student.getId());

        if (task.isFirstBloodOnly() && (attemptRepository.existsByTaskIdAndIsCorrectTrue(task.getId())
                || attemptRepository.countByTaskIdAndIsCorrect(task.getId(), true) > 0)) {
            throw new IllegalArgumentException("First Blood Only challenge has already been solved");
        }

        String filePath = fileStorageService.storeFile(file);

        Attempt attempt = Attempt.builder()
                .taskId(task.getId())
                .studentId(student.getId())
                .submittedFlag("[FILE] " + filePath)
                .filePath(filePath)
                .isCorrect(false)
                .scoreAwarded(0)
                .build();

        attemptRepository.save(attempt);

        return new ChallengeSubmitResponse(true, "File submitted successfully for grading.");
    }

    @Transactional
    public Attempt gradeAttempt(UUID attemptId, int score, double manualPercentMultiplier) {
        return gradeAttempt(attemptId, score, Double.valueOf(manualPercentMultiplier), true);
    }

    @Transactional
    public Attempt gradeAttempt(UUID attemptId, int score, Double manualPercentMultiplier) {
        return gradeAttempt(attemptId, score, manualPercentMultiplier, true);
    }

    @Transactional
    public Attempt gradeAttempt(UUID attemptId, int score, Double manualPercentMultiplier, Boolean approved) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        
        CtfTask task = getTaskOrThrow(attempt.getTaskId());

        if (Boolean.FALSE.equals(approved)) {
            attempt.setCorrect(false);
            attempt.setScoreAwarded(0);
            Attempt saved = attemptRepository.save(attempt);
            leaderboardService.broadcastUpdate();
            return saved;
        }
        
        Instant submittedAt = attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : Instant.now();
        long prevCorrectSubmissions = attemptRepository.countByTaskIdAndIsCorrectTrueAndSubmittedAtBefore(
                task.getId(), submittedAt);

        double multiplier = 0.1;
        if (prevCorrectSubmissions == 0) {
            multiplier = 1.0;
        } else if (prevCorrectSubmissions == 1) {
            multiplier = 0.5;
        }

        if (manualPercentMultiplier != null) {
            multiplier = manualPercentMultiplier;
        }
        int calculatedScore = (int) Math.round(score * multiplier);

        attempt.setCorrect(true);
        attempt.setScoreAwarded(calculatedScore);
        
        Attempt saved = attemptRepository.save(attempt);
        leaderboardService.broadcastUpdate();
        return saved;
    }

    private void validateStudentCanSubmit(Student student) {
        if (student.getStatus() == StudentStatus.PENDING_BINDING_VERIFICATION
                || student.getStatus() == StudentStatus.BLOCKED
                || student.getStatus() == StudentStatus.DISQUALIFIED) {
            throw new IllegalStateException("Student status does not allow submissions: " + student.getStatus());
        }
    }

    private boolean shouldSuppressFirstBloodBroadcast(CtfTask task, UUID studentId) {
        List<Competition> allComps = competitionRepository.findAll();
        List<Competition> relevant = allComps.stream()
                .filter(c -> c.getStatus() == CompetitionStatus.ACTIVE || (c.getTasks() != null && c.getTasks().contains(task)))
                .toList();
        if (relevant.isEmpty()) {
            relevant = allComps;
        }
        return relevant.stream().anyMatch(c ->
                c.isLeaderboardHidden()
                || (c.getHiddenStudentIds() != null && c.getHiddenStudentIds().contains(studentId)));
    }

    private Student getStudentOrThrow(UUID userId) {
        return studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
    }

    private CtfTask getTaskOrThrow(UUID taskId) {
        return ctfTaskRepository.findByIdWithLock(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
    }

    private void checkCompetitionStatusForTask(CtfTask task) {
        List<Competition> competitions = competitionRepository.findAll().stream()
                .filter(c -> c.getTasks() != null && c.getTasks().contains(task))
                .toList();

        Instant now = Instant.now();
        boolean hasActive = false;
        for (Competition comp : competitions) {
            if (comp.getStatus() == CompetitionStatus.ACTIVE) {
                boolean startsOk = comp.getStartDate() == null || !now.isBefore(comp.getStartDate());
                boolean endsOk = comp.getEndDate() == null || !now.isAfter(comp.getEndDate());
                if (startsOk && endsOk) {
                    hasActive = true;
                    break;
                }
            }
        }

        if (!hasActive && testRepository != null) {
            List<Test> publishedTests = testRepository.findAllByStatus(TestStatus.PUBLISHED);
            hasActive = publishedTests.stream()
                    .anyMatch(t -> t.getTasks() != null && t.getTasks().contains(task));
        }

        if (!hasActive) {
            if (competitions.isEmpty()) {
                throw new IllegalArgumentException("Task is not associated with any competition");
            }
            throw new IllegalArgumentException("Competition is not active");
        }
    }

    private void checkIfAlreadySolved(UUID taskId, UUID studentId) {
        if (attemptRepository.existsByTaskIdAndStudentIdAndIsCorrect(taskId, studentId, true)) {
            throw new IllegalArgumentException("Challenge already solved");
        }
    }
}
