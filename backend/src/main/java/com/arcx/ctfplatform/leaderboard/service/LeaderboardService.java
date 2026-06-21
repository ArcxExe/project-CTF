package com.arcx.ctfplatform.leaderboard.service;

import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeRepository;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.tests.entity.QuizSubmission;
import com.arcx.ctfplatform.tests.repository.QuizSubmissionRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final CompetitionRepository competitionRepository;
    private final AcademicGroupRepository academicGroupRepository;


    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public record LeaderboardEntry(UUID studentId, String username, String groupName, int score, int solvedCount) {}

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send initial state
        try {
            emitter.send(SseEmitter.event().name("INIT").data(getLeaderboardSnapshot()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastUpdate() {
        List<LeaderboardEntry> snapshot = getLeaderboardSnapshot();
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("UPDATE").data(snapshot));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        
        emitters.removeAll(deadEmitters);
    }

    public void broadcastFirstBlood(String username, String challengeTitle) {
        Map<String, String> data = Map.of("username", username, "challengeTitle", challengeTitle);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("FIRST_BLOOD").data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

    public List<LeaderboardEntry> getLeaderboardSnapshot() {
        List<Student> allStudents = studentRepository.findAll();
        List<User> allUsers = userRepository.findAll();
        Map<UUID, String> userIdToUsername = allUsers.stream().collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : u.getEmail()));
        List<AcademicGroup> allGroups = academicGroupRepository.findAll();
        Map<UUID, String> groupIdToGroupName = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, AcademicGroup::getName));

        // In a real app we would filter by competition or do group by queries. For now, doing it in memory for simplicity.
        List<Competition> competitions = competitionRepository.findAll();

        List<LeaderboardEntry> entries = new ArrayList<>();

        for (Student student : allStudents) {
            boolean isHidden = competitions.stream().anyMatch(c -> c.isLeaderboardHidden() || (c.getHiddenStudentIds() != null && c.getHiddenStudentIds().contains(student.getId())));
            if (isHidden) {
                continue;
            }

            int score = 0;
            
            // 1. Attempts
            List<Attempt> attempts = attemptRepository.findAll().stream()
                .filter(a -> a.getStudentId().equals(student.getId()) && a.isCorrect())
                .toList();
            score += attempts.stream().mapToInt(a -> a.getScoreAwarded() == null ? 0 : a.getScoreAwarded()).sum();

            // 2. Tests (if sumTestPoints is true)
            for (Competition comp : competitions) {
                if (comp.isSumTestPoints()) {
                    List<QuizSubmission> quizzes = quizSubmissionRepository.findAll().stream()
                        .filter(q -> q.getStudentId().equals(student.getId()))
                        .toList();
                    score += quizzes.stream().mapToInt(QuizSubmission::getScore).sum();
                }
            }

            // 3. Promo Codes
            List<PromoCodeClaim> claims = promoCodeClaimRepository.findAllByStudentId(student.getId());
            int additions = 0;
            int subtractions = 0;
            double multiplier = 1.0;
            for (PromoCodeClaim claim : claims) {
                PromoCode promo = claim.getPromoCode();
                if (promo != null) {
                    switch (promo.getModifierType()) {
                        case FIXED_ADD -> additions += promo.getValue();
                        case FIXED_SUB -> subtractions += promo.getValue();
                        case DOUBLE_COEFF -> multiplier *= promo.getValue();
                    }
                }
            }
            score = (int) Math.round((score + additions) * multiplier) - subtractions;
            if (score < 0) {
                score = 0;
            }


            // 4. Adjustments
            List<ScoreAdjustment> adjustments = scoreAdjustmentRepository.findAllByStudentId(student.getId());
            score += adjustments.stream().mapToInt(ScoreAdjustment::getPoints).sum();

            String username = userIdToUsername.getOrDefault(student.getUserId(), "Unknown");
            String groupName = student.getGroupId() != null ? groupIdToGroupName.getOrDefault(student.getGroupId(), "Без группы") : "Без группы";
            int solvedCount = attempts.size();
            entries.add(new LeaderboardEntry(student.getId(), username, groupName, score, solvedCount));
        }

        entries.sort((e1, e2) -> Integer.compare(e2.score(), e1.score()));
        return entries;
    }
}
