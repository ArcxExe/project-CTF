package com.arcx.ctfplatform.attempts.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.common.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import org.springframework.web.multipart.MultipartFile;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;

import lombok.RequiredArgsConstructor;
import com.arcx.ctfplatform.users.repository.UserRepository;
import com.arcx.ctfplatform.users.entity.User;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final StudentRepository studentRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final AttemptRepository attemptRepository;
    private final CompetitionRepository competitionRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    @Transactional
    public ChallengeSubmitResponse submitFlag(UUID userId, UUID taskId, String submittedFlag) {
        Student student = getStudentOrThrow(userId);
        CtfTask task = getTaskOrThrow(taskId);

        if (task.getTaskType() != TaskType.FLAG) {
            throw new IllegalArgumentException("Invalid task type for this endpoint");
        }

        checkCompetitionStatusForTask(task);
        checkIfAlreadySolved(taskId, student.getId());

        boolean isCorrect = task.getFlag().equals(submittedFlag);
        
        long solves = attemptRepository.countByTaskIdAndIsCorrect(task.getId(), true);
        boolean isFirstBlood = isCorrect && solves == 0;
        
        Integer score = isCorrect ? (task.isFirstBloodOnly() ? (isFirstBlood ? task.getMaxScore() : 0) : task.getBaseScore()) : 0;

        Attempt attempt = Attempt.builder()
                .taskId(task.getId())
                .studentId(student.getId())
                .submittedFlag(submittedFlag)
                .isCorrect(isCorrect)
                .scoreAwarded(score)
                .build();

        attemptRepository.save(attempt);

        if (isCorrect) {
            leaderboardService.broadcastUpdate();
            if (isFirstBlood) {
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
        CtfTask task = getTaskOrThrow(taskId);

        if (task.getTaskType() != TaskType.FILE_UPLOAD) {
            throw new IllegalArgumentException("Invalid task type for file upload");
        }

        checkCompetitionStatusForTask(task);
        checkIfAlreadySolved(taskId, student.getId());

        String filePath = fileStorageService.storeFile(file);

        Attempt attempt = Attempt.builder()
                .taskId(task.getId())
                .studentId(student.getId())
                .filePath(filePath)
                .isCorrect(false)
                .scoreAwarded(0)
                .build();

        attemptRepository.save(attempt);

        return new ChallengeSubmitResponse(true, "File submitted successfully for grading.");
    }

    @Transactional
    public Attempt gradeAttempt(UUID attemptId, int score, double manualPercentMultiplier) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        
        CtfTask task = getTaskOrThrow(attempt.getTaskId());
        
        long prevSubmissions = attemptRepository.countByTaskIdAndSubmittedAtBefore(task.getId(), attempt.getSubmittedAt());

        double multiplier = 0.1;
        if (prevSubmissions == 0) {
            multiplier = 1.0;
        } else if (prevSubmissions == 1) {
            multiplier = 0.5;
        }

        multiplier = multiplier * manualPercentMultiplier;
        int calculatedScore = (int) (score * multiplier);

        attempt.setCorrect(true);
        attempt.setScoreAwarded(calculatedScore);
        
        Attempt saved = attemptRepository.save(attempt);
        leaderboardService.broadcastUpdate();
        return saved;
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
                .filter(c -> c.getTasks().contains(task))
                .toList();

        if (competitions.isEmpty()) {
            throw new IllegalArgumentException("Task is not associated with any competition");
        }

        Instant now = Instant.now();
        boolean hasActive = false;
        for (Competition comp : competitions) {
            if (comp.getStatus() == CompetitionStatus.ACTIVE) {
                boolean startsOk = comp.getStartDate() == null || now.isAfter(comp.getStartDate());
                boolean endsOk = comp.getEndDate() == null || now.isBefore(comp.getEndDate());
                if (startsOk && endsOk) {
                    hasActive = true;
                    break;
                }
            }
        }

        if (!hasActive) {
            throw new IllegalArgumentException("Competition is not active");
        }
    }

    private void checkIfAlreadySolved(UUID taskId, UUID studentId) {
        if (attemptRepository.existsByTaskIdAndStudentIdAndIsCorrect(taskId, studentId, true)) {
            throw new IllegalArgumentException("Challenge already solved");
        }
    }
}
