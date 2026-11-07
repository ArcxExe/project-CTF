package com.arcx.ctfplatform.academic.service;

import com.arcx.ctfplatform.academic.attendance.dto.StudentAttendanceRowDto;
import com.arcx.ctfplatform.academic.attendance.service.AttendanceService;
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
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.LabScoreRepository;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {

    private final StudentRepository studentRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final LeaderboardService leaderboardService;
    private final AttemptRepository attemptRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final LabScoreRepository labScoreRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TestRepository testRepository;

    @Autowired(required = false)
    private StudentLabRepository studentLabRepository;

    @Autowired(required = false)
    private AttendanceService attendanceService;

    @Autowired
    public AdminAnalyticsService(
            StudentRepository studentRepository,
            AcademicGroupRepository academicGroupRepository,
            LeaderboardService leaderboardService,
            AttemptRepository attemptRepository,
            CtfTaskRepository ctfTaskRepository,
            LabScoreRepository labScoreRepository,
            QuizAttemptRepository quizAttemptRepository,
            TestRepository testRepository
    ) {
        this.studentRepository = studentRepository;
        this.academicGroupRepository = academicGroupRepository;
        this.leaderboardService = leaderboardService;
        this.attemptRepository = attemptRepository;
        this.ctfTaskRepository = ctfTaskRepository;
        this.labScoreRepository = labScoreRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.testRepository = testRepository;
    }

    public record GroupMetrics(
            int maxLabScore,
            int maxCtfScore,
            double avgLabScore,
            double avgCtfScore,
            double admissionPercentage,
            int blockedOrDisqualifiedCount,
            double avgLabPercent,
            double avgKtScore,
            double avgKtPercent,
            double avgCtfPercent,
            double avgSScore,
            double labAdmissionPercentage,
            double avgAttendancePercent,
            int grade5Count,
            int grade4Count,
            int grade3Count,
            int grade2Count
    ) {
        public GroupMetrics(
                int maxLabScore,
                int maxCtfScore,
                double avgLabScore,
                double avgCtfScore,
                double admissionPercentage,
                int blockedOrDisqualifiedCount
        ) {
            this(
                    maxLabScore,
                    maxCtfScore,
                    avgLabScore,
                    avgCtfScore,
                    admissionPercentage,
                    blockedOrDisqualifiedCount,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0,
                    0,
                    0,
                    0
            );
        }
    }

    public record StudentAnalytics(
            UUID id,
            String fullName,
            String fio,
            Integer pairNumber,
            String groupName,
            String status,
            int labScore,
            double exactLabScore,
            int acceptedLabsCount,
            boolean isEligible,
            double lPercent,
            double ktScore,
            double kPercent,
            double totalPoints,
            int ctfScore,
            double ctfCoeff,
            double cPercent,
            String token,
            double v1,
            double v2,
            double totalScore,
            double totalScore100,
            double sScore,
            String recommendedGrade,
            int numericGrade,
            double attendancePercent
    ) {
        public StudentAnalytics(
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
        ) {
            this(
                    id,
                    fullName,
                    fullName,
                    null,
                    "",
                    status,
                    labScore,
                    labScore,
                    0,
                    !"Н/Д".equals(recommendedGrade),
                    v1,
                    0.0,
                    0.0,
                    labScore,
                    ctfScore,
                    roundStatic(Math.min(ctfScore / 800.0, 1.0)),
                    v2,
                    null,
                    v1,
                    v2,
                    totalScore,
                    totalScore100,
                    totalScore100,
                    recommendedGrade,
                    parseNumericGrade(recommendedGrade),
                    0.0
            );
        }

        private static int parseNumericGrade(String rec) {
            if (rec == null || "Н/Д".equals(rec)) return 2;
            try {
                return Integer.parseInt(rec.trim());
            } catch (NumberFormatException e) {
                return 2;
            }
        }
    }

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
            String status,
            int score,
            int passingScore,
            String date
    ) {}

    public record StudentChallengeAnalytics(
            UUID studentId,
            String studentName,
            String groupName,
            UUID challengeId,
            String challengeName,
            String category,
            int maxScore,
            boolean solved,
            int scoreAwarded,
            int attemptsCount,
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
                .collect(Collectors.toMap(LeaderboardEntry::studentId, l -> l, (a, b) -> a));

        Map<UUID, Double> attendanceMap = new HashMap<>();
        if (attendanceService != null) {
            try {
                List<StudentAttendanceRowDto> attRows = attendanceService.getGroupAttendanceSheet(groupId, flowId);
                for (StudentAttendanceRowDto r : attRows) {
                    attendanceMap.put(r.studentId(), r.attendancePercent());
                }
            } catch (Exception ignored) {
            }
        }

        int maxLab = 0;
        int maxCtf = 0;
        double sumLab = 0.0;
        double sumCtf = 0.0;
        double sumLPercent = 0.0;
        double sumKtScore = 0.0;
        double sumKPercent = 0.0;
        double sumCPercent = 0.0;
        double sumSScore = 0.0;
        double sumAttendance = 0.0;
        int activeCount = 0;
        int blockedCount = 0;
        int eligibleLabsCount = 0;
        int grade5 = 0;
        int grade4 = 0;
        int grade3 = 0;
        int grade2 = 0;

        List<StudentAnalytics> studentAnalyticsList = new ArrayList<>();

        for (Student s : students) {
            LeaderboardEntry entry = leadMap.get(s.getId());
            Optional<LabScore> labScoreOpt = labScoreRepository.findByStudentId(s.getId());

            List<StudentLab> sLabs = (studentLabRepository != null)
                    ? studentLabRepository.findAllByStudentId(s.getId())
                    : Collections.emptyList();

            double exactLabScore;
            int acceptedLabsCount = 0;
            if (sLabs != null && !sLabs.isEmpty()) {
                exactLabScore = roundStatic(Math.min(sLabs.stream().mapToDouble(StudentLab::getScore).sum(), 143.0));
                acceptedLabsCount = (int) sLabs.stream().filter(StudentLab::isAccepted).count();
            } else if (labScoreOpt.isPresent()) {
                exactLabScore = labScoreOpt.get().getScore();
                acceptedLabsCount = labScoreOpt.get().isEligible() ? 8 : 0;
            } else if (entry != null && entry.lPercent() > 0) {
                exactLabScore = roundStatic((entry.lPercent() / 100.0) * 143.0);
                acceptedLabsCount = entry.isEligible() ? 8 : 0;
            } else {
                exactLabScore = 0.0;
            }

            int labScore = labScoreOpt.map(LabScore::getScore).orElse((int) Math.round(exactLabScore));
            int ctfScore = entry != null ? entry.ctfScore() : 0;

            if (labScore > maxLab) maxLab = labScore;
            if (ctfScore > maxCtf) maxCtf = ctfScore;
            sumLab += exactLabScore;
            sumCtf += ctfScore;

            if (s.getStatus() == StudentStatus.ACTIVE) {
                activeCount++;
            } else if (s.getStatus() == StudentStatus.BLOCKED
                    || s.getStatus() == StudentStatus.DISQUALIFIED
                    || s.getStatus() == StudentStatus.OUT_OF_RATING) {
                blockedCount++;
            }

            double lPercent = entry != null ? entry.lPercent() : roundStatic(Math.min((exactLabScore / 143.0) * 100.0, 100.0));
            double kPercent = entry != null ? entry.kPercent() : 0.0;
            double ktScore = roundStatic(kPercent); // Since max KT is 100.0, kPercent == ktScore
            double cPercent = entry != null ? entry.cPercent() : 0.0;
            double ctfCoeff = roundStatic(Math.min(ctfScore / 800.0, 1.0));
            double sCoeff = entry != null ? entry.sScore() : 0.0;
            double totalPoints = roundStatic(exactLabScore + ktScore);
            boolean isEligible = entry != null && entry.isEligible();
            if (isEligible && acceptedLabsCount == 0) {
                acceptedLabsCount = 8;
            }
            if (isEligible) {
                eligibleLabsCount++;
            }

            String recGrade = entry != null && entry.recommendedGrade() != null
                    ? String.valueOf(entry.recommendedGrade())
                    : "Н/Д";
            int numericGrade = (entry != null && entry.isEligible() && entry.recommendedGrade() != null)
                    ? entry.recommendedGrade()
                    : 2;

            switch (numericGrade) {
                case 5 -> grade5++;
                case 4 -> grade4++;
                case 3 -> grade3++;
                default -> grade2++;
            }

            double attPct = attendanceMap.getOrDefault(s.getId(), 0.0);

            sumLPercent += lPercent;
            sumKtScore += ktScore;
            sumKPercent += kPercent;
            sumCPercent += cPercent;
            sumSScore += sCoeff;
            sumAttendance += attPct;

            String groupName = entry != null && entry.groupName() != null
                    ? entry.groupName()
                    : (s.getAcademicGroup() != null ? s.getAcademicGroup().getName() : "Без группы");

            studentAnalyticsList.add(new StudentAnalytics(
                    s.getId(),
                    s.getFirstName() + " " + s.getLastName(),
                    s.getFullName(),
                    s.getPairNumber(),
                    groupName,
                    s.getStatus() != null ? s.getStatus().name() : "ACTIVE",
                    labScore,
                    exactLabScore,
                    acceptedLabsCount,
                    isEligible,
                    lPercent,
                    ktScore,
                    kPercent,
                    totalPoints,
                    ctfScore,
                    ctfCoeff,
                    cPercent,
                    s.getTokenInfo(),
                    lPercent,
                    cPercent,
                    sCoeff,
                    sCoeff,
                    sCoeff,
                    recGrade,
                    numericGrade,
                    attPct
            ));
        }

        int n = students.size();
        double avgLab = n == 0 ? 0.0 : roundStatic(sumLab / n);
        double avgCtf = n == 0 ? 0.0 : roundStatic(sumCtf / n);
        double admissionPct = n == 0 ? 0.0 : roundStatic(((double) activeCount / n) * 100.0);
        double avgLPercent = n == 0 ? 0.0 : roundStatic(sumLPercent / n);
        double avgKtScore = n == 0 ? 0.0 : roundStatic(sumKtScore / n);
        double avgKPercent = n == 0 ? 0.0 : roundStatic(sumKPercent / n);
        double avgCPercent = n == 0 ? 0.0 : roundStatic(sumCPercent / n);
        double avgSScore = n == 0 ? 0.0 : roundStatic(sumSScore / n);
        double labAdmissionPct = n == 0 ? 0.0 : roundStatic(((double) eligibleLabsCount / n) * 100.0);
        double avgAttPct = n == 0 ? 0.0 : roundStatic(sumAttendance / n);

        GroupMetrics metrics = new GroupMetrics(
                maxLab,
                maxCtf,
                avgLab,
                avgCtf,
                admissionPct,
                blockedCount,
                avgLPercent,
                avgKtScore,
                avgKPercent,
                avgCPercent,
                avgSScore,
                labAdmissionPct,
                avgAttPct,
                grade5,
                grade4,
                grade3,
                grade2
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
                    fbStudent = sOpt.get().getFullName();
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

            String studentName = s.getFullName();
            if (studentName.isBlank()) {
                studentName = s.getFirstName() + " " + s.getLastName();
            }
            String groupName = s.getAcademicGroup() != null ? s.getAcademicGroup().getName() : "Без группы";

            for (Test t : publishedTests) {
                QuizAttempt attempt = attemptByQuiz.get(t.getId());
                String status = "NOT_STARTED";
                int score = 0;
                String date = "-";

                if (attempt != null) {
                    status = attempt.getStatus().name();
                    score = attempt.getScore() != null ? (int) Math.round(attempt.getScore()) : 0;
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

    @Transactional(readOnly = true)
    public List<StudentChallengeAnalytics> getStudentChallengeAnalytics(UUID groupId, UUID flowId) {
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

        List<CtfTask> tasks = ctfTaskRepository.findAll();
        List<Attempt> allAttempts = attemptRepository.findAll();

        Map<UUID, List<Attempt>> attemptsByStudent = allAttempts.stream()
                .collect(Collectors.groupingBy(Attempt::getStudentId));

        List<StudentChallengeAnalytics> result = new ArrayList<>();

        for (Student s : students) {
            String studentName = s.getFullName();
            if (studentName.isBlank()) {
                studentName = s.getFirstName() + " " + s.getLastName();
            }
            String groupName = s.getAcademicGroup() != null ? s.getAcademicGroup().getName() : "Без группы";

            List<Attempt> studentAttempts = attemptsByStudent.getOrDefault(s.getId(), Collections.emptyList());
            Map<UUID, List<Attempt>> attemptsByTask = studentAttempts.stream()
                    .collect(Collectors.groupingBy(Attempt::getTaskId));

            for (CtfTask task : tasks) {
                List<Attempt> taskAttempts = attemptsByTask.getOrDefault(task.getId(), Collections.emptyList());

                boolean solved = false;
                int scoreAwarded = 0;
                int attemptsCount = taskAttempts.size();
                String date = "-";

                Optional<Attempt> correctAttemptOpt = taskAttempts.stream()
                        .filter(Attempt::isCorrect)
                        .min(Comparator.comparing(Attempt::getSubmittedAt));

                if (correctAttemptOpt.isPresent()) {
                    solved = true;
                    Attempt correct = correctAttemptOpt.get();
                    scoreAwarded = correct.getScoreAwarded() != null ? correct.getScoreAwarded() : 0;
                    date = correct.getSubmittedAt().toString();
                } else if (!taskAttempts.isEmpty()) {
                    Optional<Attempt> latestAttemptOpt = taskAttempts.stream()
                            .max(Comparator.comparing(Attempt::getSubmittedAt));
                    if (latestAttemptOpt.isPresent()) {
                        date = latestAttemptOpt.get().getSubmittedAt().toString();
                    }
                }

                result.add(new StudentChallengeAnalytics(
                        s.getId(),
                        studentName,
                        groupName,
                        task.getId(),
                        task.getTitle(),
                        task.getCategory(),
                        task.getMaxScore(),
                        solved,
                        scoreAwarded,
                        attemptsCount,
                        date
                ));
            }
        }

        return result;
    }

    private static double roundStatic(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
