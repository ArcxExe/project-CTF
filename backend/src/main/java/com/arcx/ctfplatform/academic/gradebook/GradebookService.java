package com.arcx.ctfplatform.academic.gradebook;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.GradingScale;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.entity.StudentStatus;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.academic.service.StudentScoreCalculator;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.competitions.entity.Competition;
import com.arcx.ctfplatform.competitions.repository.CompetitionRepository;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizQuestion;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.QuizQuestionRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.tests.service.KtScoreCalculator;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradebookService {

    public static final int MAX_TOKEN_LENGTH = 64;

    public static final List<String> EXPORT_HEADERS = List.of(
            "№ п/п",
            "Пара",
            "ФИО студента",
            "ЛР 1",
            "ЛР 2",
            "ЛР 3",
            "ЛР 4",
            "ЛР 5",
            "ЛР 6",
            "ЛР 7",
            "ЛР 8",
            "Общее ЛР",
            "КТ 1",
            "КТ 2",
            "КТ 3",
            "КТ 4",
            "Общее КТ",
            "Итог баллов",
            "CTF",
            "Токен",
            "Успеваемость",
            "Оценка"
    );

    private final StudentRepository studentRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final StudentLabRepository studentLabRepository;
    private final LabScoreService labScoreService;
    private final GradingService gradingService;
    private final AttemptRepository attemptRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TestRepository testRepository;
    private final PromoCodeClaimRepository promoCodeClaimRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    @Autowired(required = false)
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired(required = false)
    private CompetitionRepository competitionRepository;

    public void setQuizQuestionRepository(QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionRepository = quizQuestionRepository;
    }

    public void setCompetitionRepository(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    @Transactional(readOnly = true)
    public List<GradebookRowDto> getGradebook(UUID groupId, UUID flowId) {
        List<Student> allStudents = studentRepository.findAll();
        List<Student> students = allStudents;
        Map<UUID, AcademicGroup> groupMap = academicGroupRepository.findAll().stream()
                .collect(Collectors.toMap(AcademicGroup::getId, g -> g));

        if (groupId != null) {
            students = students.stream()
                    .filter(s -> groupId.equals(s.getGroupId()))
                    .collect(Collectors.toList());
        } else if (flowId != null) {
            students = students.stream()
                    .filter(s -> {
                        if (s.getGroupId() == null) return false;
                        AcademicGroup g = groupMap.get(s.getGroupId());
                        return g != null && flowId.equals(g.getStreamId());
                    })
                    .collect(Collectors.toList());
        }

        students.sort(
                Comparator.comparing(Student::getPairNumber, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(s -> s.getFullName().toLowerCase(Locale.ROOT))
        );

        Map<UUID, List<Attempt>> attemptsByStudent = attemptRepository.findAll().stream()
                .collect(Collectors.groupingBy(Attempt::getStudentId));
        List<QuizAttempt> allQuizAttempts = quizAttemptRepository.findAll();
        Map<UUID, List<QuizAttempt>> quizzesByStudent = allQuizAttempts.stream()
                .collect(Collectors.groupingBy(QuizAttempt::getStudentId));
        List<Test> allTests = testRepository.findAll();
        Map<UUID, Test> testMap = allTests.stream()
                .collect(Collectors.toMap(Test::getId, t -> t));
        List<QuizQuestion> allQuestions = quizQuestionRepository != null
                ? quizQuestionRepository.findAll()
                : Collections.emptyList();
        Map<UUID, Integer> questionPointsByTestId = KtScoreCalculator.computeQuestionMaxScores(allQuestions);
        KtScoreCalculator.KtMaxProfile ktMaxProfile = KtScoreCalculator.computeMaxProfile(
                allStudents,
                allTests,
                allQuizAttempts,
                questionPointsByTestId
        );
        Competition activeComp = resolveActiveCompetition();
        Map<UUID, List<PromoCodeClaim>> claimsByStudent = promoCodeClaimRepository.findAllWithPromoCode().stream()
                .filter(c -> c.getStudentId() != null)
                .collect(Collectors.groupingBy(PromoCodeClaim::getStudentId));
        Map<UUID, List<ScoreAdjustment>> adjustmentsByStudent = scoreAdjustmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(ScoreAdjustment::getStudentId));

        List<UUID> studentIds = students.stream()
                .map(Student::getId)
                .filter(Objects::nonNull)
                .toList();
        List<StudentLab> batchLabs = studentIds.isEmpty()
                ? Collections.emptyList()
                : studentLabRepository.findAllByStudentIdIn(studentIds);
        Map<UUID, List<StudentLab>> labsByStudent = (batchLabs == null ? Collections.<StudentLab>emptyList() : batchLabs)
                .stream()
                .filter(sl -> sl.getStudent() != null && sl.getStudent().getId() != null)
                .collect(Collectors.groupingBy(sl -> sl.getStudent().getId()));

        Map<UUID, LabScoreService.StudentLabSummary> labSummaries =
                labScoreService.getLabSummariesForStudents(studentIds);
        if (labSummaries == null) {
            labSummaries = Collections.emptyMap();
        }
        Optional<List<GradingScale>> preloadedScales = gradingService.loadPreloadedScales();
        if (preloadedScales == null) {
            preloadedScales = Optional.empty();
        }

        List<GradebookRowDto> rows = new ArrayList<>(students.size());
        int rowIndex = 1;
        for (Student student : students) {
            rows.add(buildRowForStudent(
                    student,
                    rowIndex++,
                    groupMap,
                    attemptsByStudent.getOrDefault(student.getId(), Collections.emptyList()),
                    quizzesByStudent.getOrDefault(student.getId(), Collections.emptyList()),
                    testMap,
                    ktMaxProfile,
                    activeComp,
                    claimsByStudent.getOrDefault(student.getId(), Collections.emptyList()),
                    adjustmentsByStudent.getOrDefault(student.getId(), Collections.emptyList()),
                    labsByStudent.get(student.getId()),
                    labSummaries.get(student.getId()),
                    preloadedScales
            ));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Optional<GradebookRowDto> getMyGradebookRow(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return Optional.empty();
        }
        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail));
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Student> studentOpt = studentRepository.findByUserId(userOpt.get().getId());
        return studentOpt.map(this::buildSingleStudentRow);
    }

    @Transactional
    public GradebookRowDto updateStudentGradebook(UUID studentId, GradebookUpdateRequest req) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        if (req.pairNumber() != null) {
            student.setPairNumber(req.pairNumber() > 0 ? req.pairNumber() : null);
        }
        if (req.kt1() != null) {
            student.setKt1(roundToTwoDecimals(Math.max(0.0, req.kt1())));
        }
        if (req.kt2() != null) {
            student.setKt2(roundToTwoDecimals(Math.max(0.0, req.kt2())));
        }
        if (req.kt3() != null) {
            student.setKt3(roundToTwoDecimals(Math.max(0.0, req.kt3())));
        }
        if (req.kt4() != null) {
            student.setKt4(roundToTwoDecimals(Math.max(0.0, req.kt4())));
        }
        if (req.token() != null) {
            String trimmedToken = req.token().trim();
            if (trimmedToken.length() > MAX_TOKEN_LENGTH) {
                throw new IllegalArgumentException("Token must not exceed 64 characters");
            }
            student.setTokenInfo(trimmedToken.isEmpty() ? null : trimmedToken);
        }
        if (Boolean.TRUE.equals(req.clearExamScore())) {
            student.setExamScore(null);
        } else if (req.examScore() != null) {
            if (req.examScore() < 0 || req.examScore() > 100) {
                throw new IllegalArgumentException("Exam score must be between 0 and 100");
            }
            student.setExamScore(req.examScore());
        }
        studentRepository.save(student);

        boolean labsChanged = false;
        if (req.labScores() != null) {
            for (Map.Entry<Integer, Double> entry : req.labScores().entrySet()) {
                int labNum = entry.getKey();
                if (labNum < 1 || labNum > 8 || entry.getValue() == null) {
                    continue;
                }
                LabScoreService.LabSpec spec = LabScoreService.getLabSpec(labNum);
                double clampedScore = roundToTwoDecimals(Math.max(0.0, Math.min(entry.getValue(), spec.maxTotal())));
                Optional<StudentLab> existingLabOpt = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNum);
                boolean hasExplicitAccepted = req.labAccepted() != null
                        && req.labAccepted().get(labNum) != null;

                if (existingLabOpt.isEmpty() && clampedScore == 0.0 && !hasExplicitAccepted) {
                    continue;
                }

                StudentLab lab = existingLabOpt
                        .orElseGet(() -> StudentLab.builder().student(student).labNumber(labNum).build());
                boolean scoreChanged = existingLabOpt.isEmpty()
                        || Math.abs(roundToTwoDecimals(lab.getScore()) - clampedScore) >= 0.005;

                if (!scoreChanged && !hasExplicitAccepted) {
                    continue;
                }

                lab.setMaxRawScore(spec.maxRawScore());
                lab.setMaxDefenseScore(spec.maxDefenseScore());

                if (scoreChanged) {
                    double ratio = spec.maxTotal() > 0 ? (clampedScore / spec.maxTotal()) : 0.0;
                    double recalculatedRaw = roundToTwoDecimals(spec.maxRawScore() * ratio);
                    double recalculatedDefense = roundToTwoDecimals(
                            Math.min(spec.maxDefenseScore(), Math.max(0.0, clampedScore - recalculatedRaw))
                    );
                    lab.setRawScore(recalculatedRaw);
                    lab.setDefenseScore(recalculatedDefense);
                    lab.setPenaltyCoefficient(1.0);
                    lab.setScore(clampedScore);
                }

                boolean accepted;
                if (hasExplicitAccepted) {
                    accepted = Boolean.TRUE.equals(req.labAccepted().get(labNum));
                } else {
                    accepted = lab.getRawScore() >= 0.40 * spec.maxRawScore()
                            && lab.getDefenseScore() >= 0.50 * spec.maxDefenseScore()
                            && lab.getScore() >= 0.40 * spec.maxTotal();
                }
                lab.setAccepted(accepted);
                studentLabRepository.save(lab);
                labsChanged = true;
            }
        }
        if (req.labAccepted() != null && !req.labAccepted().isEmpty()) {
            for (Map.Entry<Integer, Boolean> entry : req.labAccepted().entrySet()) {
                int labNum = entry.getKey();
                if (labNum < 1 || labNum > 8 || entry.getValue() == null) {
                    continue;
                }
                if (req.labScores() != null && req.labScores().get(labNum) != null) {
                    continue;
                }
                LabScoreService.LabSpec spec = LabScoreService.getLabSpec(labNum);
                StudentLab lab = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNum)
                        .orElseGet(() -> StudentLab.builder()
                                .student(student)
                                .labNumber(labNum)
                                .maxRawScore(spec.maxRawScore())
                                .maxDefenseScore(spec.maxDefenseScore())
                                .penaltyCoefficient(1.0)
                                .build());
                lab.setAccepted(entry.getValue());
                studentLabRepository.save(lab);
                labsChanged = true;
            }
        }
        if (labsChanged) {
            labScoreService.updateAggregatedLabScore(student);
        }

        leaderboardService.broadcastUpdate();

        return buildSingleStudentRow(student);
    }

    @Transactional
    public GradebookRowDto updateLabDetail(LabDetailUpdateRequest req) {
        if (req.studentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }
        double penalty = req.penaltyCoefficient() != null ? req.penaltyCoefficient() : 1.0;
        if (Double.isNaN(penalty) || Double.isInfinite(penalty) || penalty < 0.0 || penalty > 1.0) {
            throw new IllegalArgumentException("Penalty coefficient must be between 0.0 and 1.0");
        }
        labScoreService.recordLab(req.studentId(), req.labNumber(), req.rawScore(), req.defenseScore(), penalty);
        Student student = studentRepository.findById(req.studentId()).orElseThrow();
        if (req.acceptedOverride() != null) {
            studentLabRepository.findByStudentIdAndLabNumber(req.studentId(), req.labNumber())
                    .ifPresent(lab -> {
                        lab.setAccepted(req.acceptedOverride());
                        studentLabRepository.save(lab);
                    });
            labScoreService.updateAggregatedLabScore(student);
            if (leaderboardService != null) {
                leaderboardService.broadcastUpdate();
            }
        }
        return buildSingleStudentRow(student);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID groupId, UUID flowId) {
        List<GradebookRowDto> rows = getGradebook(groupId, flowId);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM for Excel Cyrillic compatibility
        sb.append(String.join(",", EXPORT_HEADERS.stream().map(this::escapeCsv).toList())).append("\n");

        for (GradebookRowDto r : rows) {
            List<String> cols = new ArrayList<>(22);
            cols.add(String.valueOf(r.rowNumber()));
            cols.add(r.pairNumber() != null ? String.valueOf(r.pairNumber()) : "");
            cols.add(escapeCsv(r.fullName()));
            for (int i = 1; i <= 8; i++) {
                GradebookRowDto.LabCellDto cell = r.labs().get(i);
                cols.add(formatDecimal(cell != null ? cell.score() : 0.0));
            }
            cols.add(formatDecimal(r.totalLab()));
            cols.add(formatDecimal(r.kt1()));
            cols.add(formatDecimal(r.kt2()));
            cols.add(formatDecimal(r.kt3()));
            cols.add(formatDecimal(r.kt4()));
            cols.add(formatDecimal(r.totalKt()));
            cols.add(formatDecimal(r.totalPoints()));
            cols.add(formatDecimal(r.ctfCoeff()));
            cols.add(escapeCsv(r.token() != null ? r.token() : ""));
            cols.add(formatDecimal(r.sScore()));
            cols.add(String.valueOf(r.grade()));
            sb.append(String.join(",", cols)).append("\n");
        }

        double maxKt1 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt1();
        double maxKt2 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt2();
        double maxKt3 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt3();
        double maxKt4 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt4();
        double maxTotalKt = rows.isEmpty() ? 100.0 : rows.get(0).maxTotalKt();
        double maxTotalPoints = rows.isEmpty() ? 243.0 : rows.get(0).maxTotalPoints();

        // MAX footer row matching attached Рейтинг spreadsheet
        List<String> maxRow = List.of(
                "",
                "",
                "MAX",
                formatDecimal(10.0),
                formatDecimal(12.0),
                formatDecimal(12.0),
                formatDecimal(15.0),
                formatDecimal(24.0),
                formatDecimal(28.0),
                formatDecimal(20.0),
                formatDecimal(22.0),
                formatDecimal(143.0),
                formatCompactNumber(maxKt1),
                formatCompactNumber(maxKt2),
                formatCompactNumber(maxKt3),
                formatCompactNumber(maxKt4),
                formatDecimal(maxTotalKt),
                formatDecimal(maxTotalPoints),
                formatDecimal(1.0),
                "",
                "100.00",
                "5"
        );
        sb.append(String.join(",", maxRow)).append("\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx(UUID groupId, UUID flowId) {
        List<GradebookRowDto> rows = getGradebook(groupId, flowId);
        List<List<Object>> sheetRows = new ArrayList<>(rows.size() + 1);

        for (GradebookRowDto r : rows) {
            List<Object> cols = new ArrayList<>(22);
            cols.add(r.rowNumber());
            cols.add(r.pairNumber() != null ? r.pairNumber() : "");
            cols.add(r.fullName());
            for (int i = 1; i <= 8; i++) {
                GradebookRowDto.LabCellDto cell = r.labs().get(i);
                cols.add(cell != null ? cell.score() : 0.0);
            }
            cols.add(r.totalLab());
            cols.add(r.kt1());
            cols.add(r.kt2());
            cols.add(r.kt3());
            cols.add(r.kt4());
            cols.add(r.totalKt());
            cols.add(r.totalPoints());
            cols.add(r.ctfCoeff());
            cols.add(r.token() != null ? r.token() : "");
            cols.add(r.sScore());
            cols.add(r.grade());
            sheetRows.add(cols);
        }

        double maxKt1 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt1();
        double maxKt2 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt2();
        double maxKt3 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt3();
        double maxKt4 = rows.isEmpty() ? 25.0 : rows.get(0).maxKt4();
        double maxTotalKt = rows.isEmpty() ? 100.0 : rows.get(0).maxTotalKt();
        double maxTotalPoints = rows.isEmpty() ? 243.0 : rows.get(0).maxTotalPoints();

        List<Object> maxRow = new ArrayList<>(22);
        maxRow.add("");
        maxRow.add("");
        maxRow.add("MAX");
        maxRow.add(10.0);
        maxRow.add(12.0);
        maxRow.add(12.0);
        maxRow.add(15.0);
        maxRow.add(24.0);
        maxRow.add(28.0);
        maxRow.add(20.0);
        maxRow.add(22.0);
        maxRow.add(143.0);
        maxRow.add(maxKt1 == Math.floor(maxKt1) ? (int) maxKt1 : maxKt1);
        maxRow.add(maxKt2 == Math.floor(maxKt2) ? (int) maxKt2 : maxKt2);
        maxRow.add(maxKt3 == Math.floor(maxKt3) ? (int) maxKt3 : maxKt3);
        maxRow.add(maxKt4 == Math.floor(maxKt4) ? (int) maxKt4 : maxKt4);
        maxRow.add(maxTotalKt);
        maxRow.add(maxTotalPoints);
        maxRow.add(1.0);
        maxRow.add("");
        maxRow.add(100.0);
        maxRow.add(5);
        sheetRows.add(maxRow);

        return SimpleXlsxUtil.writeSheet("Рейтинг", EXPORT_HEADERS, sheetRows);
    }

    private Competition resolveActiveCompetition() {
        if (competitionRepository == null) {
            return null;
        }
        return StudentScoreCalculator.selectActiveCompetition(competitionRepository.findAll(), Instant.now());
    }

    private GradebookRowDto buildSingleStudentRow(Student student) {
        Map<UUID, AcademicGroup> groupMap = new HashMap<>();
        if (student.getGroupId() != null) {
            academicGroupRepository.findById(student.getGroupId())
                    .ifPresent(group -> groupMap.put(group.getId(), group));
        }
        List<Attempt> attempts = attemptRepository.findAllByStudentId(student.getId());
        List<QuizAttempt> quizzes = quizAttemptRepository.findAllByStudentId(student.getId());
        Set<UUID> quizIds = quizzes.stream()
                .map(QuizAttempt::getQuizId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Test> testMap = quizIds.isEmpty()
                ? Collections.emptyMap()
                : testRepository.findAllById(quizIds).stream()
                        .collect(Collectors.toMap(Test::getId, t -> t, (a, b) -> a));
        List<PromoCodeClaim> claims = promoCodeClaimRepository.findAllByStudentIdWithPromoCode(student.getId());
        List<ScoreAdjustment> adjustments = scoreAdjustmentRepository.findAllByStudentId(student.getId());
        List<StudentLab> studentLabs = studentLabRepository.findAllByStudentId(student.getId());
        LabScoreService.StudentLabSummary labSummary = new LabScoreService.StudentLabSummary(
                labScoreService.getFinalLabScore(student.getId()),
                labScoreService.isEligible(student.getId())
        );
        Optional<List<GradingScale>> preloadedScales = gradingService.loadPreloadedScales();
        if (preloadedScales == null) {
            preloadedScales = Optional.empty();
        }
        List<Test> ktTests = new ArrayList<>(testMap.values());
        List<QuizQuestion> ktQuestions = (quizQuestionRepository != null && !quizIds.isEmpty())
                ? quizQuestionRepository.findAllByTestIdIn(quizIds)
                : Collections.emptyList();
        Map<UUID, Integer> questionPointsByTestId = KtScoreCalculator.computeQuestionMaxScores(ktQuestions);
        KtScoreCalculator.KtMaxProfile ktMaxProfile = KtScoreCalculator.computeMaxProfile(
                List.of(student),
                ktTests,
                quizzes,
                questionPointsByTestId
        );
        Competition activeComp = resolveActiveCompetition();
        return buildRowForStudent(
                student,
                1,
                groupMap,
                attempts,
                quizzes,
                testMap,
                ktMaxProfile,
                activeComp,
                claims,
                adjustments,
                studentLabs,
                labSummary,
                preloadedScales
        );
    }

    private GradebookRowDto buildRowForStudent(
            Student student,
            int rowNumber,
            Map<UUID, AcademicGroup> groupMap,
            List<Attempt> attempts,
            List<QuizAttempt> quizzes,
            Map<UUID, Test> testMap,
            KtScoreCalculator.KtMaxProfile ktMaxProfile,
            Competition activeComp,
            List<PromoCodeClaim> claims,
            List<ScoreAdjustment> adjustments,
            List<StudentLab> preloadedLabs,
            LabScoreService.StudentLabSummary preloadedLabSummary,
            Optional<List<GradingScale>> preloadedScales
    ) {
        boolean isDisqualified = student.getStatus() == StudentStatus.DISQUALIFIED;

        List<StudentLab> studentLabs = isDisqualified
                ? Collections.emptyList()
                : (preloadedLabs != null
                        ? preloadedLabs
                        : studentLabRepository.findAllByStudentId(student.getId()));
        Map<Integer, StudentLab> labByNum = studentLabs.stream()
                .collect(Collectors.toMap(StudentLab::getLabNumber, l -> l, (a, b) -> b));

        Map<Integer, GradebookRowDto.LabCellDto> labCells = new LinkedHashMap<>();
        int acceptedCount = 0;
        for (int i = 1; i <= 8; i++) {
            LabScoreService.LabSpec spec = LabScoreService.getLabSpec(i);
            StudentLab sl = labByNum.get(i);
            double score = sl != null ? roundToTwoDecimals(sl.getScore()) : 0.0;
            double rawScore = sl != null ? roundToTwoDecimals(sl.getRawScore()) : 0.0;
            double defScore = sl != null ? roundToTwoDecimals(sl.getDefenseScore()) : 0.0;
            double penalty = sl != null && sl.getPenaltyCoefficient() >= 0.0 && sl.getPenaltyCoefficient() <= 1.0
                    ? sl.getPenaltyCoefficient()
                    : 1.0;
            boolean accepted = sl != null && sl.isAccepted();
            if (accepted) {
                acceptedCount++;
            }
            labCells.put(i, new GradebookRowDto.LabCellDto(
                    i,
                    score,
                    spec.maxTotal(),
                    rawScore,
                    spec.maxRawScore(),
                    defScore,
                    spec.maxDefenseScore(),
                    penalty,
                    accepted
            ));
        }

        double rawLabScore = 0.0;
        boolean labEligible = false;
        if (!isDisqualified) {
            rawLabScore = preloadedLabSummary != null
                    ? preloadedLabSummary.finalScore()
                    : labScoreService.getFinalLabScore(student.getId());
            labEligible = preloadedLabSummary != null
                    ? preloadedLabSummary.isEligible()
                    : labScoreService.isEligible(student.getId());
        }

        AcademicGroup group = student.getGroupId() != null ? groupMap.get(student.getGroupId()) : null;
        UUID studentFlowId = group != null ? group.getStreamId() : null;

        StudentScoreCalculator.StudentMetrics metrics = StudentScoreCalculator.calculateMetrics(
                student,
                rawLabScore,
                labEligible,
                attempts,
                quizzes,
                testMap,
                ktMaxProfile,
                activeComp,
                claims,
                adjustments,
                studentFlowId,
                gradingService,
                preloadedScales
        );

        double maxTotalKt = ktMaxProfile.totalKtMax();
        double maxTotalPoints = roundToTwoDecimals(143.0 + maxTotalKt);
        String groupName = group != null ? group.getName() : "Без группы";
        String statusStr = student.getStatus() != null ? student.getStatus().name().toLowerCase(Locale.ROOT) : "active";

        return new GradebookRowDto(
                student.getId(),
                rowNumber,
                student.getPairNumber(),
                student.getFullName(),
                student.getStudentCode(),
                student.getGroupId(),
                groupName,
                statusStr,
                labCells,
                metrics.totalLab(),
                acceptedCount,
                metrics.isEligible(),
                metrics.lPercent(),
                metrics.ktResult().kt1(),
                metrics.ktResult().kt2(),
                metrics.ktResult().kt3(),
                metrics.ktResult().kt4(),
                metrics.ktResult().totalKt(),
                metrics.ktResult().kPercent(),
                metrics.totalPoints(),
                metrics.ctfScore(),
                metrics.ctfCoeff(),
                metrics.cPercent(),
                student.getTokenInfo(),
                metrics.sScore(),
                student.getExamScore(),
                metrics.recommendedGrade(),
                metrics.numericGrade(),
                ktMaxProfile.kt1Max(),
                ktMaxProfile.kt2Max(),
                ktMaxProfile.kt3Max(),
                ktMaxProfile.kt4Max(),
                maxTotalKt,
                maxTotalPoints
        );
    }

    private String formatCompactNumber(double val) {
        double rounded = roundToTwoDecimals(val);
        if (rounded == Math.floor(rounded)) {
            return String.valueOf((long) rounded);
        }
        return formatDecimal(rounded);
    }

    private String formatDecimal(double val) {
        return String.format(Locale.US, "%.2f", roundToTwoDecimals(val));
    }

    private String escapeCsv(String val) {
        if (val == null || val.isEmpty()) return "";
        String sanitized = val;
        char first = sanitized.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            sanitized = "'" + sanitized;
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
