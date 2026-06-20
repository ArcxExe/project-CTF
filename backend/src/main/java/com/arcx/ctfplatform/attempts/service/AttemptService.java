package com.arcx.ctfplatform.attempts.service;

import java.time.Instant;
import java.util.UUID;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final StudentRepository studentRepository;
    private final ChallengeRepository challengeRepository;
    private final AttemptRepository attemptRepository;
    private final CompetitionRepository competitionRepository;

    @Transactional
    public ChallengeSubmitResponse submitFlag(UUID userId, UUID challengeId, String submittedFlag) {
        Student student = studentRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        Competition competition = competitionRepository.findById(challenge.getCompetitionId())
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

        if (attemptRepository.existsByChallengeIdAndStudentIdAndIsCorrect(challenge.getId(), student.getId(), true)) {
            throw new IllegalArgumentException("Challenge already solved");
        }

        boolean isCorrect = challenge.getFlag().equals(submittedFlag);

        Attempt attempt = Attempt.builder()
                .challengeId(challenge.getId())
                .studentId(student.getId())
                .submittedFlag(submittedFlag)
                .isCorrect(isCorrect)
                .build();

        attemptRepository.save(attempt);

        String message = isCorrect ? "Correct flag!" : "Incorrect flag, try again.";
        return new ChallengeSubmitResponse(isCorrect, message);
    }
}
