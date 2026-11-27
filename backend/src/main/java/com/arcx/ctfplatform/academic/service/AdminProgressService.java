package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.dto.AdminProgressResponse;
import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminProgressService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final AttemptRepository attemptRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TestRepository testRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final CompetitionRepository competitionRepository;

    @Transactional(readOnly = true)
    public List<AdminProgressResponse> getAdminProgress() {
        List<Student> allStudents = studentRepository.findAll();
        List<User> allUsers = userRepository.findAll();
        Map<UUID, String> userIdToUsername = allUsers.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getUsername() != null ? u.getUsername() : u.getEmail(),
                        (a, b) -> a
                ));

        List<AcademicGroup> allGroups = academicGroupRepository.findAll();
        Map<UUID, String> groupIdToGroupName = allGroups.stream()
                .collect(Collectors.toMap(AcademicGroup::getId, AcademicGroup::getName, (a, b) -> a));

        List<Competition> competitions = competitionRepository.findAll();
        Competition activeComp = StudentScoreCalculator.selectActiveCompetition(competitions, Instant.now());

        List<Attempt> allAttempts = attemptRepository.findAll();
        Map<UUID, List<Attempt>> attemptsByStudent = allAttempts.stream()
                .collect(Collectors.groupingBy(Attempt::getStudentId));

        List<QuizAttempt> allQuizAttempts = quizAttemptRepository.findAll();
        Map<UUID, List<QuizAttempt>> quizAttemptsByStudent = allQuizAttempts.stream()
                .collect(Collectors.groupingBy(QuizAttempt::getStudentId));
        List<Test> allTests = testRepository.findAll();
        Map<UUID, Test> testMap = allTests.stream()
                .collect(Collectors.toMap(Test::getId, t -> t, (a, b) -> a));

        List<PromoCodeClaim> allClaims = promoCodeClaimRepository.findAllWithPromoCode();
        Map<UUID, List<PromoCodeClaim>> claimsByStudent = allClaims.stream()
                .filter(c -> c.getStudentId() != null)
                .collect(Collectors.groupingBy(PromoCodeClaim::getStudentId));

        List<ScoreAdjustment> allAdjustments = scoreAdjustmentRepository.findAll();
        Map<UUID, List<ScoreAdjustment>> adjustmentsByStudent = allAdjustments.stream()
                .collect(Collectors.groupingBy(ScoreAdjustment::getStudentId));

        List<AdminProgressResponse> responses = new ArrayList<>();

        for (Student student : allStudents) {
            UUID studentId = student.getId();

            List<Attempt> attempts = attemptsByStudent.getOrDefault(studentId, Collections.emptyList());
            List<QuizAttempt> quizzes = quizAttemptsByStudent.getOrDefault(studentId, Collections.emptyList());
            List<PromoCodeClaim> claims = claimsByStudent.getOrDefault(studentId, Collections.emptyList());
            List<ScoreAdjustment> adjustments = adjustmentsByStudent.getOrDefault(studentId, Collections.emptyList());

            StudentScoreCalculator.CtfScoreResult ctfResult = StudentScoreCalculator.calculateCtfScore(
                    student,
                    attempts,
                    quizzes,
                    testMap,
                    activeComp,
                    claims,
                    adjustments
            );

            int attemptsCount = attempts.size();
            String username = student.getUserId() != null
                    ? userIdToUsername.getOrDefault(student.getUserId(), "Unknown")
                    : "Unknown";
            String groupName = student.getGroupId() != null
                    ? groupIdToGroupName.getOrDefault(student.getGroupId(), "Без группы")
                    : "Без группы";

            responses.add(new AdminProgressResponse(
                    studentId,
                    username,
                    groupName,
                    ctfResult.ctfScore(),
                    ctfResult.solvedCount(),
                    attemptsCount
            ));
        }

        return responses;
    }
}
