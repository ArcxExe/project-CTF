package com.arcx.ctfplatform.academic.controller;

import com.arcx.ctfplatform.academic.entity.AcademicGroup;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.gradebook.GradebookRowDto;
import com.arcx.ctfplatform.academic.gradebook.GradebookService;
import com.arcx.ctfplatform.academic.repository.AcademicGroupRepository;
import com.arcx.ctfplatform.academic.repository.StudentRepository;
import com.arcx.ctfplatform.attempts.entity.Attempt;
import com.arcx.ctfplatform.attempts.repository.AttemptRepository;
import com.arcx.ctfplatform.challenges.entity.CtfTask;
import com.arcx.ctfplatform.challenges.repository.CtfTaskRepository;
import com.arcx.ctfplatform.common.util.SimpleXlsxUtil;
import com.arcx.ctfplatform.modifiers.entity.ScoreAdjustment;
import com.arcx.ctfplatform.modifiers.repository.ScoreAdjustmentRepository;
import com.arcx.ctfplatform.students.entity.lab.LabScoreHistory;
import com.arcx.ctfplatform.students.repository.lab.LabScoreHistoryRepository;
import com.arcx.ctfplatform.users.entity.User;
import com.arcx.ctfplatform.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
@RequiredArgsConstructor
public class ReportController {

    private final GradebookService gradebookService;
    private final StudentRepository studentRepository;
    private final AcademicGroupRepository academicGroupRepository;
    private final AttemptRepository attemptRepository;
    private final CtfTaskRepository ctfTaskRepository;
    private final ScoreAdjustmentRepository scoreAdjustmentRepository;
    private final LabScoreHistoryRepository labScoreHistoryRepository;
    private final UserRepository userRepository;

    public record FinalStudentReportRow(
            UUID studentId,
            String fullName,
            String studentCode,
            String groupName,
            String flowName,
            double labScore,
            double v1Percent,
            double ktScore,
            double kPercent,
            int ctfScore,
            double v2Percent,
            int adjustmentsSum,
            double sScore,
            Integer recommendedGrade,
            int grade,
            boolean isEligible
    ) {}

    public record SolutionReportRow(
            UUID attemptId,
            UUID studentId,
            String studentName,
            String groupName,
            UUID taskId,
            String taskTitle,
            String category,
            String submittedFlag,
            boolean correct,
            int scoreAwarded,
            Instant submittedAt
    ) {}

    public record ManualReviewReportRow(
            UUID attemptId,
            UUID studentId,
            String studentName,
            String groupName,
            UUID taskId,
            String taskTitle,
            String filePath,
            boolean correct,
            int scoreAwarded,
            Instant submittedAt
    ) {}

    public record AdminAdjustmentReportRow(
            UUID id,
            String adjustmentType,
            UUID studentId,
            String studentName,
            String groupName,
            int deltaOrNewScore,
            String reason,
            String author,
            Instant timestamp
    ) {}

    @GetMapping("/final-students")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getFinalStudentsReport(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID flowId,
            @RequestParam(required = false) String format
    ) {
        List<GradebookRowDto> gradebookRows = gradebookService.getGradebook(groupId, flowId);
        Map<UUID, AcademicGroup> groupMap = academicGroupRepository.findAll().stream()
                .collect(Collectors.toMap(AcademicGroup::getId, g -> g));
        Map<UUID, Integer> adjustmentsByStudent = scoreAdjustmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        ScoreAdjustment::getStudentId,
                        Collectors.summingInt(a -> a.getPoints() != null ? a.getPoints() : 0)
                ));

        List<FinalStudentReportRow> rows = gradebookRows.stream()
                .map(r -> {
                    AcademicGroup grp = r.groupId() != null ? groupMap.get(r.groupId()) : null;
                    String flowName = (grp != null && grp.getAcademicFlow() != null) ? grp.getAcademicFlow().getName() : "";
                    int adj = adjustmentsByStudent.getOrDefault(r.studentId(), 0);
                    return new FinalStudentReportRow(
                            r.studentId(),
                            r.fullName(),
                            r.studentCode(),
                            r.groupName(),
                            flowName,
                            r.totalLab(),
                            r.lPercent(),
                            r.totalKt(),
                            r.kPercent(),
                            r.ctfScore(),
                            r.cPercent(),
                            adj,
                            r.sScore(),
                            r.recommendedGrade(),
                            r.grade(),
                            r.isEligible()
                    );
                })
                .toList();

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }

        List<String> headers = List.of(
                "ФИО", "Код студента", "Группа", "Поток", "Баллы ЛР (M)", "v1 (L%)",
                "Баллы КТ", "K%", "Баллы CTF (N)", "v2 (C%)", "Бонусы/Штрафы", "Итог S%",
                "Рекомендуемая оценка", "Итоговая оценка", "Допуск (8/8 ЛР)"
        );

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder sb = new StringBuilder("\uFEFF");
            sb.append(String.join(",", headers.stream().map(this::escapeCsv).toList())).append("\n");
            for (FinalStudentReportRow r : rows) {
                sb.append(String.join(",",
                        escapeCsv(r.fullName()),
                        escapeCsv(r.studentCode()),
                        escapeCsv(r.groupName()),
                        escapeCsv(r.flowName()),
                        String.format(Locale.US, "%.2f", r.labScore()),
                        String.format(Locale.US, "%.2f", r.v1Percent()),
                        String.format(Locale.US, "%.2f", r.ktScore()),
                        String.format(Locale.US, "%.2f", r.kPercent()),
                        String.valueOf(r.ctfScore()),
                        String.format(Locale.US, "%.2f", r.v2Percent()),
                        String.valueOf(r.adjustmentsSum()),
                        String.format(Locale.US, "%.2f", r.sScore()),
                        r.recommendedGrade() != null ? String.valueOf(r.recommendedGrade()) : "Н/Д",
                        String.valueOf(r.grade()),
                        r.isEligible() ? "Да" : "Нет"
                )).append("\n");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"final-student-report.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        List<List<Object>> sheetRows = new ArrayList<>(rows.size());
        for (FinalStudentReportRow r : rows) {
            List<Object> cols = new ArrayList<>();
            cols.add(r.fullName());
            cols.add(r.studentCode());
            cols.add(r.groupName());
            cols.add(r.flowName());
            cols.add(r.labScore());
            cols.add(r.v1Percent());
            cols.add(r.ktScore());
            cols.add(r.kPercent());
            cols.add(r.ctfScore());
            cols.add(r.v2Percent());
            cols.add(r.adjustmentsSum());
            cols.add(r.sScore());
            cols.add(r.recommendedGrade() != null ? r.recommendedGrade() : "Н/Д");
            cols.add(r.grade());
            cols.add(r.isEligible() ? "Да" : "Нет");
            sheetRows.add(cols);
        }
        byte[] xlsxBytes = SimpleXlsxUtil.writeSheet("Итоговый отчет", headers, sheetRows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"final-student-report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxBytes);
    }

    @GetMapping("/solutions")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSolutionsReport(
            @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) Boolean correct,
            @RequestParam(required = false) String format
    ) {
        Map<UUID, Student> studentMap = studentRepository.findAllWithAcademicGroup().stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Map<UUID, CtfTask> taskMap = ctfTaskRepository.findAll().stream()
                .collect(Collectors.toMap(CtfTask::getId, t -> t));

        List<SolutionReportRow> rows = attemptRepository.findAll().stream()
                .filter(a -> taskId == null || taskId.equals(a.getTaskId()))
                .filter(a -> studentId == null || studentId.equals(a.getStudentId()))
                .filter(a -> correct == null || correct.booleanValue() == a.isCorrect())
                .sorted(Comparator.comparing(Attempt::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> {
                    Student st = studentMap.get(a.getStudentId());
                    CtfTask task = taskMap.get(a.getTaskId());
                    String studentName = st != null ? st.getFullName() : "";
                    String groupName = (st != null && st.getAcademicGroup() != null) ? st.getAcademicGroup().getName() : "Без группы";
                    String taskTitle = task != null ? task.getTitle() : "";
                    String category = task != null && task.getCategory() != null ? task.getCategory() : "";
                    return new SolutionReportRow(
                            a.getId(),
                            a.getStudentId(),
                            studentName,
                            groupName,
                            a.getTaskId(),
                            taskTitle,
                            category,
                            a.getSubmittedFlag(),
                            a.isCorrect(),
                            a.getScoreAwarded() != null ? a.getScoreAwarded() : 0,
                            a.getSubmittedAt()
                    );
                })
                .toList();

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID Попытки,ФИО Студента,Группа,Задача,Категория,Ответ/Флаг,Верно,Баллы,Время сдачи\n");
        for (SolutionReportRow r : rows) {
            sb.append(String.join(",",
                    String.valueOf(r.attemptId()),
                    escapeCsv(r.studentName()),
                    escapeCsv(r.groupName()),
                    escapeCsv(r.taskTitle()),
                    escapeCsv(r.category()),
                    escapeCsv(r.submittedFlag() != null ? r.submittedFlag() : ""),
                    r.correct() ? "Да" : "Нет",
                    String.valueOf(r.scoreAwarded()),
                    r.submittedAt() != null ? r.submittedAt().toString() : ""
            )).append("\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"solutions-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/manual-reviews")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getManualReviewsReport(
            @RequestParam(required = false) String format
    ) {
        Map<UUID, Student> studentMap = studentRepository.findAllWithAcademicGroup().stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Map<UUID, CtfTask> taskMap = ctfTaskRepository.findAll().stream()
                .collect(Collectors.toMap(CtfTask::getId, t -> t));

        List<ManualReviewReportRow> rows = attemptRepository.findAll().stream()
                .filter(a -> a.getFilePath() != null && !a.getFilePath().isBlank())
                .sorted(Comparator.comparing(Attempt::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> {
                    Student st = studentMap.get(a.getStudentId());
                    CtfTask task = taskMap.get(a.getTaskId());
                    String studentName = st != null ? st.getFullName() : "";
                    String groupName = (st != null && st.getAcademicGroup() != null) ? st.getAcademicGroup().getName() : "Без группы";
                    String taskTitle = task != null ? task.getTitle() : "";
                    return new ManualReviewReportRow(
                            a.getId(),
                            a.getStudentId(),
                            studentName,
                            groupName,
                            a.getTaskId(),
                            taskTitle,
                            a.getFilePath(),
                            a.isCorrect(),
                            a.getScoreAwarded() != null ? a.getScoreAwarded() : 0,
                            a.getSubmittedAt()
                    );
                })
                .toList();

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID Попытки,ФИО Студента,Группа,Задача,Файл,Принято,Баллы,Время отправки\n");
        for (ManualReviewReportRow r : rows) {
            sb.append(String.join(",",
                    String.valueOf(r.attemptId()),
                    escapeCsv(r.studentName()),
                    escapeCsv(r.groupName()),
                    escapeCsv(r.taskTitle()),
                    escapeCsv(r.filePath()),
                    r.correct() ? "Да" : "Нет",
                    String.valueOf(r.scoreAwarded()),
                    r.submittedAt() != null ? r.submittedAt().toString() : ""
            )).append("\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"manual-reviews.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin-adjustments")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAdminAdjustmentsReport(
            @RequestParam(required = false) String format
    ) {
        Map<UUID, Student> studentMap = studentRepository.findAllWithAcademicGroup().stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Map<UUID, String> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<AdminAdjustmentReportRow> rows = new ArrayList<>();

        for (ScoreAdjustment sa : scoreAdjustmentRepository.findAll()) {
            Student st = studentMap.get(sa.getStudentId());
            String studentName = st != null ? st.getFullName() : "";
            String groupName = (st != null && st.getAcademicGroup() != null) ? st.getAcademicGroup().getName() : "Без группы";
            rows.add(new AdminAdjustmentReportRow(
                    sa.getId(),
                    "CTF_SCORE_ADJUSTMENT",
                    sa.getStudentId(),
                    studentName,
                    groupName,
                    sa.getPoints() != null ? sa.getPoints() : 0,
                    sa.getReason(),
                    "ADMIN",
                    sa.getCreatedAt()
            ));
        }

        for (LabScoreHistory lh : labScoreHistoryRepository.findAll()) {
            Student st = lh.getStudent();
            UUID stId = st != null ? st.getId() : null;
            Student mappedSt = stId != null ? studentMap.getOrDefault(stId, st) : null;
            String studentName = mappedSt != null ? mappedSt.getFullName() : "";
            String groupName = (mappedSt != null && mappedSt.getAcademicGroup() != null)
                    ? mappedSt.getAcademicGroup().getName()
                    : "Без группы";
            String author = lh.getChangedByUserId() != null
                    ? userMap.getOrDefault(lh.getChangedByUserId(), lh.getChangedByUserId().toString())
                    : "ADMIN";
            rows.add(new AdminAdjustmentReportRow(
                    lh.getId(),
                    "LAB_SCORE_CHANGE",
                    stId,
                    studentName,
                    groupName,
                    lh.getNewScore(),
                    lh.getReason(),
                    author,
                    lh.getCreatedAt()
            ));
        }

        rows.sort(Comparator.comparing(AdminAdjustmentReportRow::timestamp, Comparator.nullsLast(Comparator.reverseOrder())));

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID,Тип изменения,ФИО Студента,Группа,Значение,Причина,Автор,Время\n");
        for (AdminAdjustmentReportRow r : rows) {
            sb.append(String.join(",",
                    String.valueOf(r.id()),
                    escapeCsv(r.adjustmentType()),
                    escapeCsv(r.studentName()),
                    escapeCsv(r.groupName()),
                    String.valueOf(r.deltaOrNewScore()),
                    escapeCsv(r.reason() != null ? r.reason() : ""),
                    escapeCsv(r.author()),
                    r.timestamp() != null ? r.timestamp().toString() : ""
            )).append("\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-adjustments.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
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
}
