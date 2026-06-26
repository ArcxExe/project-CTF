package com.arcx.ctfplatform.academic.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService.LeaderboardEntry;
import com.arcx.ctfplatform.students.entity.lab.LabScore;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.TestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final StudentRepository studentRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final LeaderboardService leaderboardService;
    private final AttemptRepository attemptRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final LabScoreRepository labScoreRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TestRepository testRepository;

    // DTO records
    public record GroupMetrics(
        int maxLabScore,
        int maxCtfScore,
        double avgLabScore,
        double avgCtfScore,
        double admissionPercentage,
        int blockedOrDisqualifiedCount
    ) {}

    public record StudentAnalytics(
        UUID id,
        String fullName,
        String status,
        int labScore,
        int ctfScore,
        double v1,
        double v2,
        double totalScore,
        double totalScore100,
        String recommendedGrade
    ) {}

    public record AnalyticsSummary(
        GroupMetrics metrics,
        List<StudentAnalytics> students
    ) {}

    public record TaskAnalytics(
        UUID challengeId,
        String challengeName,
        double solvePercentage,
        String firstBloodStudentName,
        String firstBloodTime,
        int incorrectAttempts
    ) {}

    public record StudentTestAnalytics(
        UUID studentId,
        String studentName,
        String groupName,
        UUID testId,
        String testTitle,
        String status, // COMPLETED, IN_PROGRESS, NOT_STARTED
        int score,
        int passingScore,
        String date
    ) {}

    @Transactional(readOnly = true)
    public AnalyticsSummary getGroupAnalytics(UUID groupId, UUID flowId) {
        List<Student> students = studentRepository.findAll();
        
        if (groupId != null) {
            students = students.stream().filter(s -> groupId.equals(s.getGroupId())).toList();
        } else if (flowId != null) {
            students = students.stream()
                .filter(s -> s.getGroupId() != null)
                .filter(s -> {
                    AcademicGroup g = academicGroupRepository.findById(s.getGroupId()).orElse(null);
                    return g != null && flowId.equals(g.getStreamId());
                }).toList();
        }

        // Get leaderboard snap to extract metrics
        String scopeType = "GLOBAL";
        UUID scopeId = null;
        if (groupId != null) {
            scopeType = "GROUP";
            scopeId = groupId;
        } else if (flowId != null) {
            scopeType = "FLOW";
            scopeId = flowId;
        }
        
        List<LeaderboardEntry> leaderboard = leaderboardService.getLeaderboardSnapshot(scopeType, scopeId);
        Map<UUID, LeaderboardEntry> leadMap = leaderboard.stream()
            .collect(Collectors.toMap(LeaderboardEntry::studentId, l -> l));

        int maxLab = 0;
        int maxCtf = 0;
        double sumLab = 0.0;
        double sumCtf = 0.0;
        int activeCount = 0;
        int blockedCount = 0;

        List<StudentAnalytics> studentAnalyticsList = new ArrayList<>();

        for (Student s : students) {
            LeaderboardEntry entry = leadMap.get(s.getId());
            Optional<LabScore> labScoreOpt = labScoreRepository.findByStudentId(s.getId());
            int labScore = labScoreOpt.map(LabScore::getScore).orElse(0);
            int ctfScore = entry != null ? entry.score() : 0;
            
            if (labScore > maxLab) maxLab = labScore;
            if (ctfScore > maxCtf) maxCtf = ctfScore;
            sumLab += labScore;
            sumCtf += ctfScore;

            if (s.getStatus() == StudentStatus.ACTIVE) {
                activeCount++;
            } else if (s.getStatus() == StudentStatus.BLOCKED || 
                       s.getStatus() == StudentStatus.DISQUALIFIED || 
                       s.getStatus() == StudentStatus.OUT_OF_RATING) {
                blockedCount++;
            }

            double v1 = entry != null ? entry.v1() : 0.0;
            double v2 = entry != null ? entry.v2() : 0.0;
            double sCoeff = entry != null ? entry.sCoefficient() : 0.0;
            double totalScore100 = sCoeff * 100.0;
            String recGrade = entry != null ? String.valueOf(entry.recommendedGrade()) : "2";

            studentAnalyticsList.add(new StudentAnalytics(
                s.getId(),
                s.getFirstName() + " " + s.getLastName(),
                s.getStatus().name(),
                labScore,
                ctfScore,
                v1,
                v2,
                sCoeff,
                totalScore100,
                recGrade
            ));
        }

        double avgLab = students.isEmpty() ? 0.0 : sumLab / students.size();
        double avgCtf = students.isEmpty() ? 0.0 : sumCtf / students.size();
        double admissionPct = students.isEmpty() ? 0.0 : ((double) activeCount / students.size()) * 100.0;

        GroupMetrics metrics = new GroupMetrics(
            maxLab,
            maxCtf,
            avgLab,
            avgCtf,
            admissionPct,
            blockedCount
        );

        return new AnalyticsSummary(metrics, studentAnalyticsList);
    }

    @Transactional(readOnly = true)
    public List<TaskAnalytics> getTaskAnalytics(UUID groupId, UUID flowId) {
        List<Student> students = studentRepository.findAll();
        if (groupId != null) {
            students = students.stream().filter(s -> groupId.equals(s.getGroupId())).toList();
        } else if (flowId != null) {
            students = students.stream()
                .filter(s -> s.getGroupId() != null)
                .filter(s -> {
                    AcademicGroup g = academicGroupRepository.findById(s.getGroupId()).orElse(null);
                    return g != null && flowId.equals(g.getStreamId());
                }).toList();
        }
        
        Set<UUID> studentIds = students.stream().map(Student::getId).collect(Collectors.toSet());

        List<CtfTask> tasks = ctfTaskRepository.findAll();
        List<Attempt> allAttempts = attemptRepository.findAll();
        Map<UUID, List<Attempt>> attemptsByTask = allAttempts.stream()
            .collect(Collectors.groupingBy(Attempt::getTaskId));

        List<TaskAnalytics> taskAnalyticsList = new ArrayList<>();

        for (CtfTask task : tasks) {
            List<Attempt> attempts = attemptsByTask.getOrDefault(task.getId(), Collections.emptyList());
            
            // Filter attempts to only match students in group/flow
            List<Attempt> filteredAttempts = attempts.stream()
                .filter(a -> studentIds.contains(a.getStudentId()))
                .toList();

            List<Attempt> correctAttempts = filteredAttempts.stream()
                .filter(Attempt::isCorrect)
                .sorted(Comparator.comparing(Attempt::getSubmittedAt))
                .toList();

            long solvedStudentsCount = correctAttempts.stream()
                .map(Attempt::getStudentId)
                .distinct()
                .count();

            double solvePercentage = studentIds.isEmpty() ? 0.0 : ((double) solvedStudentsCount / studentIds.size()) * 100.0;

            String fbStudent = "-";
            String fbTime = "-";
            if (!correctAttempts.isEmpty()) {
                Attempt fb = correctAttempts.get(0);
                Optional<Student> sOpt = studentRepository.findById(fb.getStudentId());
                if (sOpt.isPresent()) {
                    fbStudent = sOpt.get().getFirstName() + " " + sOpt.get().getLastName();
                    fbTime = fb.getSubmittedAt().toString();
                }
            }

            int incorrectCount = (int) filteredAttempts.stream().filter(a -> !a.isCorrect()).count();

            taskAnalyticsList.add(new TaskAnalytics(
                task.getId(),
                task.getTitle(),
                solvePercentage,
                fbStudent,
                fbTime,
                incorrectCount
            ));
        }

        return taskAnalyticsList;
    }

    @Transactional(readOnly = true)
    public List<StudentTestAnalytics> getStudentTestAnalytics(UUID groupId, UUID flowId) {
        List<Student> students = studentRepository.findAll();
        if (groupId != null) {
            students = students.stream().filter(s -> groupId.equals(s.getGroupId())).toList();
        } else if (flowId != null) {
            students = students.stream()
                .filter(s -> s.getGroupId() != null)
                .filter(s -> {
                    AcademicGroup g = academicGroupRepository.findById(s.getGroupId()).orElse(null);
                    return g != null && flowId.equals(g.getStreamId());
                }).toList();
        }

        List<Test> tests = testRepository.findAll();
        List<Test> publishedTests = tests.stream()
            .filter(t -> t.getStatus() == com.arcx.ctfplatform.tests.entity.TestStatus.PUBLISHED)
            .toList();

        List<QuizAttempt> allAttempts = quizAttemptRepository.findAll();
        Map<UUID, List<QuizAttempt>> attemptsByStudent = allAttempts.stream()
            .collect(Collectors.groupingBy(QuizAttempt::getStudentId));

        List<StudentTestAnalytics> result = new ArrayList<>();

        for (Student s : students) {
            List<QuizAttempt> studentAttempts = attemptsByStudent.getOrDefault(s.getId(), Collections.emptyList());
            Map<UUID, QuizAttempt> attemptByQuiz = studentAttempts.stream()
                .collect(Collectors.toMap(QuizAttempt::getQuizId, qa -> qa, (qa1, qa2) -> qa1));

            String studentName = s.getFirstName() + " " + s.getLastName();
            String groupName = s.getAcademicGroup() != null ? s.getAcademicGroup().getName() : "Без группы";

            for (Test t : publishedTests) {
                QuizAttempt attempt = attemptByQuiz.get(t.getId());
                String status = "NOT_STARTED";
                int score = 0;
                String date = "-";

                if (attempt != null) {
                    status = attempt.getStatus().name();
                    score = attempt.getScore();
                    Instant time = attempt.getCompletedAt() != null ? attempt.getCompletedAt() : attempt.getStartedAt();
                    if (time != null) {
                        date = time.toString();
                    }
                }

                result.add(new StudentTestAnalytics(
                    s.getId(),
                    studentName,
                    groupName,
                    t.getId(),
                    t.getTitle(),
                    status,
                    score,
                    t.getPassingScore(),
                    date
                ));
            }
        }

        return result;
    }
}
