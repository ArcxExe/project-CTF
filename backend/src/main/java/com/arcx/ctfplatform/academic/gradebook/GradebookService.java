package com.arcx.ctfplatform.academic.gradebook;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.academic.service.GradingService;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.leaderboard.service.LeaderboardService;
import com.arcx.ctfplatform.modifiers.entity.PromoCode;
import com.arcx.ctfplatform.modifiers.entity.PromoCodeClaim;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.PromoCodeClaimRepository;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.entity.lab.StudentLab;
import com.arcx.ctfplatform.students.repository.lab.StudentLabRepository;
import com.arcx.ctfplatform.students.service.lab.LabScoreService;
import com.arcx.ctfplatform.tests.entity.QuizAttempt;
import com.arcx.ctfplatform.tests.entity.QuizAttemptStatus;
import com.arcx.ctfplatform.tests.entity.Test;
import com.arcx.ctfplatform.tests.repository.QuizAttemptRepository;
import com.arcx.ctfplatform.tests.repository.TestRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradebookService {

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

    @Transactional(readOnly = true)
    public List<GradebookRowDto> getGradebook(UUID groupId, UUID flowId) {
        List<Student> students = studentRepository.findAll();
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
        Map<UUID, List<QuizAttempt>> quizzesByStudent = quizAttemptRepository.findAll().stream()
                .collect(Collectors.groupingBy(QuizAttempt::getStudentId));
        Map<UUID, Test> testMap = testRepository.findAll().stream()
                .collect(Collectors.toMap(Test::getId, t -> t));
        Map<UUID, List<PromoCodeClaim>> claimsByStudent = promoCodeClaimRepository.findAllWithPromoCode().stream()
                .filter(c -> c.getStudentId() != null)
                .collect(Collectors.groupingBy(PromoCodeClaim::getStudentId));
        Map<UUID, List<ScoreAdjustment>> adjustmentsByStudent = scoreAdjustmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(ScoreAdjustment::getStudentId));

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
                    claimsByStudent.getOrDefault(student.getId(), Collections.emptyList()),
                    adjustmentsByStudent.getOrDefault(student.getId(), Collections.emptyList())
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
        if (studentOpt.isEmpty()) {
            return Optional.empty();
        }
        Student student = studentOpt.get();
        List<GradebookRowDto> rows = getGradebook(student.getGroupId(), null);
        return rows.stream().filter(r -> r.studentId().equals(student.getId())).findFirst();
    }

    @Transactional
    public GradebookRowDto updateStudentGradebook(UUID studentId, GradebookUpdateRequest req) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        if (req.pairNumber() != null) {
            student.setPairNumber(req.pairNumber() > 0 ? req.pairNumber() : null);
        }
        if (req.kt1() != null) {
            student.setKt1(roundToTwoDecimals(Math.max(0.0, Math.min(req.kt1(), 25.0))));
        }
        if (req.kt2() != null) {
            student.setKt2(roundToTwoDecimals(Math.max(0.0, Math.min(req.kt2(), 25.0))));
        }
        if (req.kt3() != null) {
            student.setKt3(roundToTwoDecimals(Math.max(0.0, Math.min(req.kt3(), 25.0))));
        }
        if (req.kt4() != null) {
            student.setKt4(roundToTwoDecimals(Math.max(0.0, Math.min(req.kt4(), 25.0))));
        }
        if (req.token() != null) {
            student.setTokenInfo(req.token().trim().isEmpty() ? null : req.token().trim());
        }
        if (req.examScore() != null) {
            student.setExamScore(req.examScore() >= 2 && req.examScore() <= 5 ? req.examScore() : null);
        }
        studentRepository.save(student);

        if (req.labScores() != null) {
            for (Map.Entry<Integer, Double> entry : req.labScores().entrySet()) {
                int labNum = entry.getKey();
                if (labNum < 1 || labNum > 8 || entry.getValue() == null) {
                    continue;
                }
                LabScoreService.LabSpec spec = LabScoreService.getLabSpec(labNum);
                double clampedScore = roundToTwoDecimals(Math.max(0.0, Math.min(entry.getValue(), spec.maxTotal())));
                boolean accepted;
                if (req.labAccepted() != null && req.labAccepted().containsKey(labNum)) {
                    accepted = Boolean.TRUE.equals(req.labAccepted().get(labNum));
                } else {
                    accepted = clampedScore >= 0.40 * spec.maxTotal();
                }

                StudentLab lab = studentLabRepository.findByStudentIdAndLabNumber(studentId, labNum)
                        .orElseGet(() -> StudentLab.builder().student(student).labNumber(labNum).build());
                lab.setMaxRawScore(spec.maxRawScore());
                lab.setMaxDefenseScore(spec.maxDefenseScore());
                double ratio = spec.maxTotal() > 0 ? (clampedScore / spec.maxTotal()) : 0.0;
                lab.setRawScore(roundToTwoDecimals(spec.maxRawScore() * ratio));
                lab.setDefenseScore(roundToTwoDecimals(clampedScore - lab.getRawScore()));
                lab.setPenaltyCoefficient(1.0);
                lab.setScore(clampedScore);
                lab.setAccepted(accepted);
                studentLabRepository.save(lab);
            }
            // Refresh LabScore summary row
            double sum = studentLabRepository.findAllByStudentId(studentId).stream()
                    .mapToDouble(StudentLab::getScore)
                    .sum();
            boolean eligible = labScoreService.isEligible(studentId);
            labScoreService.setScore(studentId, (int) Math.round(sum), eligible, "Gradebook update", null);
        } else if (req.labAccepted() != null && !req.labAccepted().isEmpty()) {
            for (Map.Entry<Integer, Boolean> entry : req.labAccepted().entrySet()) {
                int labNum = entry.getKey();
                if (labNum < 1 || labNum > 8 || entry.getValue() == null) {
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
            }
        }

        leaderboardService.broadcastUpdate();

        List<GradebookRowDto> groupRows = getGradebook(student.getGroupId(), null);
        return groupRows.stream()
                .filter(r -> r.studentId().equals(studentId))
                .findFirst()
                .orElseGet(() -> getGradebook(null, null).stream()
                        .filter(r -> r.studentId().equals(studentId))
                        .findFirst()
                        .orElseThrow());
    }

    @Transactional
    public GradebookRowDto updateLabDetail(LabDetailUpdateRequest req) {
        if (req.studentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }
        double penalty = req.penaltyCoefficient() > 0 ? req.penaltyCoefficient() : 1.0;
        labScoreService.recordLab(req.studentId(), req.labNumber(), req.rawScore(), req.defenseScore(), penalty);
        if (req.acceptedOverride() != null) {
            studentLabRepository.findByStudentIdAndLabNumber(req.studentId(), req.labNumber())
                    .ifPresent(lab -> {
                        lab.setAccepted(req.acceptedOverride());
                        studentLabRepository.save(lab);
                    });
        }
        Student student = studentRepository.findById(req.studentId()).orElseThrow();
        return getGradebook(student.getGroupId(), null).stream()
                .filter(r -> r.studentId().equals(req.studentId()))
                .findFirst()
                .orElseThrow();
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
                "25",
                "25",
                "25",
                "25",
                formatDecimal(100.0),
                formatDecimal(243.0),
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
        maxRow.add(25);
        maxRow.add(25);
        maxRow.add(25);
        maxRow.add(25);
        maxRow.add(100.0);
        maxRow.add(243.0);
        maxRow.add(1.0);
        maxRow.add("");
        maxRow.add(100.0);
        maxRow.add(5);
        sheetRows.add(maxRow);

        return SimpleXlsxUtil.writeSheet("Рейтинг", EXPORT_HEADERS, sheetRows);
    }

    private GradebookRowDto buildRowForStudent(
            Student student,
            int rowNumber,
            Map<UUID, AcademicGroup> groupMap,
            List<Attempt> attempts,
            List<QuizAttempt> quizzes,
            Map<UUID, Test> testMap,
            List<PromoCodeClaim> claims,
            List<ScoreAdjustment> adjustments
    ) {
        List<StudentLab> studentLabs = studentLabRepository.findAllByStudentId(student.getId());
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
            double penalty = sl != null && sl.getPenaltyCoefficient() > 0 ? sl.getPenaltyCoefficient() : 1.0;
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

        double totalLab = roundToTwoDecimals(Math.min(labScoreService.getFinalLabScore(student.getId()), 143.0));
        boolean isEligible = labScoreService.isEligible(student.getId());
        if (studentLabs.isEmpty() && isEligible) {
            acceptedCount = 8;
        }
        double lPercent = roundToTwoDecimals(Math.max(0.0, Math.min((totalLab / 143.0) * 100.0, 100.0)));

        // KT 1..4
        double[] slotKt = new double[] {
                roundToTwoDecimals(Math.max(0.0, Math.min(student.getKt1(), 25.0))),
                roundToTwoDecimals(Math.max(0.0, Math.min(student.getKt2(), 25.0))),
                roundToTwoDecimals(Math.max(0.0, Math.min(student.getKt3(), 25.0))),
                roundToTwoDecimals(Math.max(0.0, Math.min(student.getKt4(), 25.0)))
        };
        Map<UUID, Double> unslottedKt = new HashMap<>();
        for (QuizAttempt qa : quizzes) {
            if (qa.getStatus() == QuizAttemptStatus.COMPLETED) {
                Test t = testMap.get(qa.getQuizId());
                if (t != null && t.isControlPoint()) {
                    double qScore = qa.getScore() != null ? Math.max(0.0, qa.getScore().doubleValue()) : 0.0;
                    double capped = roundToTwoDecimals(Math.min(qScore, 25.0));
                    Integer ktNum = t.getEffectiveKtNumber();
                    if (ktNum != null && ktNum >= 1 && ktNum <= 4) {
                        slotKt[ktNum - 1] = Math.max(slotKt[ktNum - 1], capped);
                    } else {
                        unslottedKt.merge(t.getId(), capped, Math::max);
                    }
                }
            }
        }
        double totalKt = slotKt[0] + slotKt[1] + slotKt[2] + slotKt[3]
                + unslottedKt.values().stream().mapToDouble(Double::doubleValue).sum();
        totalKt = roundToTwoDecimals(Math.min(totalKt, 100.0));
        double kPercent = roundToTwoDecimals(Math.max(0.0, Math.min((totalKt / 100.0) * 100.0, 100.0)));

        double totalPoints = roundToTwoDecimals(totalLab + totalKt);

        // CTF score
        int rawCtf = attempts.stream()
                .filter(Attempt::isCorrect)
                .mapToInt(a -> a.getScoreAwarded() == null ? 0 : a.getScoreAwarded())
                .sum();
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
        int ctfScore = (int) Math.round((rawCtf + additions) * multiplier) - subtractions;
        if (ctfScore < 0) ctfScore = 0;
        ctfScore += adjustments.stream().mapToInt(ScoreAdjustment::getPoints).sum();
        if (ctfScore < 0) ctfScore = 0;

        double ctfCoeff = roundToTwoDecimals(Math.max(0.0, Math.min(ctfScore / 800.0, 1.00)));
        double cPercent = roundToTwoDecimals(Math.max(0.0, Math.min((ctfScore / 800.0) * 100.0, 100.0)));

        double sScore = roundToTwoDecimals(0.50 * lPercent + 0.25 * kPercent + 0.25 * cPercent);

        Integer recommendedGrade = null;
        if (isEligible) {
            Integer baseGrade = gradingService.calculateGrade(sScore, lPercent, kPercent);
            if (student.getExamScore() != null && baseGrade != null) {
                int clampedExam = Math.max(2, Math.min(5, student.getExamScore()));
                recommendedGrade = Math.max(baseGrade, clampedExam);
            } else {
                recommendedGrade = baseGrade;
            }
        }
        int numericGrade = (recommendedGrade != null) ? recommendedGrade : 2;

        AcademicGroup group = student.getGroupId() != null ? groupMap.get(student.getGroupId()) : null;
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
                totalLab,
                acceptedCount,
                isEligible,
                lPercent,
                slotKt[0],
                slotKt[1],
                slotKt[2],
                slotKt[3],
                totalKt,
                kPercent,
                totalPoints,
                ctfScore,
                ctfCoeff,
                cPercent,
                student.getTokenInfo(),
                sScore,
                student.getExamScore(),
                recommendedGrade,
                numericGrade
        );
    }

    private String formatDecimal(double val) {
        return String.format(Locale.US, "%.2f", roundToTwoDecimals(val));
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
