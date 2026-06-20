package com.arcx.ctfplatform.attempts.service;

import java.time.Instant;
import java.util.UUID;

import com.arcx.ctfplatform.challenges.entity.TaskType;
import com.arcx.ctfplatform.common.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.attempts.dto.ChallengeSubmitResponse;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.Challenge;
import com.arcx.ctfplatform.challenges.repository.ChallengeRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.entity.CompetitionStatus;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.students.entity.Student;
import com.arcx.ctfplatform.students.repository.StudentRepository;
import org.springframework.web.multipart.MultipartFile;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;

import lombok.RequiredArgsConstructor;
import com.arcx.ctfplatform.users.repository.UserRepository;
import com.arcx.ctfplatform.users.entity.User;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final StudentRepository studentRepository;
    private final ChallengeRepository challengeRepository;
    private final AttemptRepository attemptRepository;
    private final CompetitionRepository competitionRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    @Transactional
    public ChallengeSubmitResponse submitFlag(UUID userId, UUID challengeId, String submittedFlag) {
        Student student = getStudentOrThrow(userId);
        Challenge challenge = getChallengeOrThrow(challengeId);

        if (challenge.getTaskType() != TaskType.FLAG) {
            throw new IllegalArgumentException("Invalid task type for this endpoint");
        }

        checkCompetitionStatus(challenge.getCompetitionId());
        checkIfAlreadySolved(challengeId, student.getId());

        boolean isCorrect = challenge.getFlag().equals(submittedFlag);
        
        long solves = attemptRepository.countByChallengeIdAndIsCorrect(challenge.getId(), true);
        boolean isFirstBlood = isCorrect && solves == 0;
        
        Integer score = isCorrect ? (challenge.isFirstBloodOnly() ? (isFirstBlood ? challenge.getMaxScore() : 0) : challenge.getPoints()) : 0;

        Attempt attempt = Attempt.builder()
                .challengeId(challenge.getId())
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
                leaderboardService.broadcastFirstBlood(username, challenge.getTitle());
            }
        }

        String message = isCorrect ? "Correct flag! You earned " + score + " points." : "Incorrect flag, try again.";
        return new ChallengeSubmitResponse(isCorrect, message);
    }

    @Transactional
    public ChallengeSubmitResponse submitFile(UUID userId, UUID challengeId, MultipartFile file) {
        Student student = getStudentOrThrow(userId);
        Challenge challenge = getChallengeOrThrow(challengeId);

        if (challenge.getTaskType() != TaskType.FILE_UPLOAD) {
            throw new IllegalArgumentException("Invalid task type for file upload");
        }

        checkCompetitionStatus(challenge.getCompetitionId());
        checkIfAlreadySolved(challengeId, student.getId());

        String filePath = fileStorageService.storeFile(file);

        Attempt attempt = Attempt.builder()
                .challengeId(challenge.getId())
                .studentId(student.getId())
                .filePath(filePath)
                .isCorrect(false) // Needs manual grading
                .scoreAwarded(0)
                .build();

        attemptRepository.save(attempt);

        return new ChallengeSubmitResponse(true, "File submitted successfully for grading.");
    }

    @Transactional
    public Attempt gradeAttempt(UUID attemptId, int score, double manualPercentMultiplier) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        
        Challenge challenge = getChallengeOrThrow(attempt.getChallengeId());
        
        long prevSubmissions = attemptRepository.countByChallengeIdAndSubmittedAtBefore(challenge.getId(), attempt.getSubmittedAt());

        double multiplier = 0.1;
        if (prevSubmissions == 0) {
            multiplier = 1.0;
        } else if (prevSubmissions == 1) {
            multiplier = 0.5;
        }

        // Apply manual multiplier
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

    private Challenge getChallengeOrThrow(UUID challengeId) {
        // Find with lock for First Blood thread safety
        return challengeRepository.findByIdWithLock(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
    }

    private void checkCompetitionStatus(UUID competitionId) {
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Competition not found"));

        if (competition.getStatus() != CompetitionStatus.PUBLISHED) {
            throw new IllegalArgumentException("Competition is not active");
        }

        Instant now = Instant.now();
        if (competition.getStartsAt() != null && now.isBefore(competition.getStartsAt())) {
            throw new IllegalArgumentException("Competition is not active");
        }
        if (competition.getEndsAt() != null && now.isAfter(competition.getEndsAt())) {
            throw new IllegalArgumentException("Competition is not active");
        }
    }

    private void checkIfAlreadySolved(UUID challengeId, UUID studentId) {
        if (attemptRepository.existsByChallengeIdAndStudentIdAndIsCorrect(challengeId, studentId, true)) {
            throw new IllegalArgumentException("Challenge already solved");
        }
    }

    private Integer calculateScore(Challenge challenge) {
        if (challenge.isFirstBloodOnly()) {
            long solves = attemptRepository.countByChallengeIdAndIsCorrect(challenge.getId(), true);
            if (solves == 0) {
                return challenge.getMaxScore();
            } else {
                return 0; // First blood already taken
            }
        }
        return challenge.getPoints(); // Or some degraded calculation if needed, but for normal flag it's just points
    }
}
