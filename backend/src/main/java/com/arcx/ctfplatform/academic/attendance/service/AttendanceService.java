package com.arcx.ctfplatform.academic.attendance.service;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arcx.ctfplatform.academic.attendance.dto.StudentAttendanceRowDto;
import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;
import com.arcx.ctfplatform.academic.attendance.entity.SessionType;
import com.arcx.ctfplatform.academic.attendance.entity.StudentAttendance;
import com.arcx.ctfplatform.academic.attendance.repository.StudentAttendanceRepository;
import com.arcx.ctfplatform.academic.entity.Student;
import com.arcx.ctfplatform.academic.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Attendance accounting for 8 lectures and 8 practices (regulation 3.4).
 * The module is intentionally isolated from the rating score.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final int MIN_CYCLE_NUMBER = 1;
    public static final int MAX_CYCLE_NUMBER = 8;

    private static final String RUSSIAN_LOCALE = "ru-RU";

    private final StudentRepository studentRepository;
    private final StudentAttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public List<StudentAttendanceRowDto> getGroupAttendanceSheet(UUID groupId, UUID streamId) {
        List<Student> students = loadStudents(groupId, streamId);
        if (students.isEmpty()) {
            return List.of();
        }

        List<UUID> studentIds = students.stream().map(Student::getId).toList();
        Map<UUID, List<StudentAttendance>> attendanceByStudent = attendanceRepository.findByStudentIdIn(studentIds)
                .stream()
                .collect(Collectors.groupingBy(StudentAttendance::getStudentId));

        sortByFullName(students);

        List<StudentAttendanceRowDto> rows = new ArrayList<>(students.size());
        for (int index = 0; index < students.size(); index++) {
            Student student = students.get(index);
            List<StudentAttendance> records = attendanceByStudent.getOrDefault(student.getId(), List.of());
            rows.add(toRow(student, index + 1, records));
        }
        return rows;
    }

    /**
     * Builds the attendance sheet of an academic group (or of all students when {@code groupId} is null).
     * Students are ordered alphabetically by full name.
     */
    @Transactional(readOnly = true)
    public List<StudentAttendanceRowDto> getGroupAttendanceSheet(UUID groupId) {
        return getGroupAttendanceSheet(groupId, null);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<StudentAttendanceRowDto> getStudentAttendanceByUserId(UUID userId) {
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return studentRepository.findByUserId(userId).map(this::buildStudentRow);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceCsv(UUID groupId, UUID streamId) {
        List<StudentAttendanceRowDto> rows = getGroupAttendanceSheet(groupId, streamId);
        List<String> headers = buildExportHeaders();
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(",", headers.stream().map(this::escapeCsv).toList())).append("\n");

        for (StudentAttendanceRowDto r : rows) {
            List<String> cols = new ArrayList<>();
            cols.add(String.valueOf(r.rowNumber()));
            cols.add(r.pairNumber() != null ? String.valueOf(r.pairNumber()) : "");
            cols.add(escapeCsv(r.fullName()));
            cols.add(escapeCsv(r.groupName() != null ? r.groupName() : ""));
            for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
                cols.add(formatStatus(r.lectures() != null ? r.lectures().get(c) : null));
            }
            for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
                cols.add(formatStatus(r.practices() != null ? r.practices().get(c) : null));
            }
            cols.add(String.valueOf(r.lecturesPresentCount()));
            cols.add(String.valueOf(r.practicesPresentCount()));
            cols.add(String.valueOf(r.excusedCount()));
            cols.add(String.format(Locale.US, "%.1f", r.attendancePercent()));
            sb.append(String.join(",", cols)).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceXlsx(UUID groupId, UUID streamId) {
        List<StudentAttendanceRowDto> rows = getGroupAttendanceSheet(groupId, streamId);
        List<String> headers = buildExportHeaders();
        List<List<Object>> sheetRows = new ArrayList<>(rows.size());

        for (StudentAttendanceRowDto r : rows) {
            List<Object> cols = new ArrayList<>();
            cols.add(r.rowNumber());
            cols.add(r.pairNumber() != null ? r.pairNumber() : "");
            cols.add(r.fullName());
            cols.add(r.groupName() != null ? r.groupName() : "");
            for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
                cols.add(formatStatus(r.lectures() != null ? r.lectures().get(c) : null));
            }
            for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
                cols.add(formatStatus(r.practices() != null ? r.practices().get(c) : null));
            }
            cols.add(r.lecturesPresentCount());
            cols.add(r.practicesPresentCount());
            cols.add(r.excusedCount());
            cols.add(r.attendancePercent());
            sheetRows.add(cols);
        }

        return com.arcx.ctfplatform.common.util.SimpleXlsxUtil.writeSheet("Посещаемость", headers, sheetRows);
    }

    private List<String> buildExportHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("№ п/п");
        headers.add("Пара");
        headers.add("ФИО студента");
        headers.add("Группа");
        for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
            headers.add("Л" + c);
        }
        for (int c = MIN_CYCLE_NUMBER; c <= MAX_CYCLE_NUMBER; c++) {
            headers.add("П" + c);
        }
        headers.add("Лекций (из 8)");
        headers.add("Практик (из 8)");
        headers.add("Уваж. пропуск");
        headers.add("Посещаемость %");
        return headers;
    }

    private String formatStatus(AttendanceStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PRESENT -> "+";
            case ABSENT -> "н";
            case EXCUSED -> "у";
        };
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

    /**
     * Updates a single attendance cell. A {@code null} status clears the cell,
     * a {@code null} comment keeps the previously stored comment.
     *
     * @return the recalculated sheet row of the student
     */
    @Transactional
    public StudentAttendanceRowDto updateCell(UUID studentId, SessionType sessionType, int cycleNumber,
                                              AttendanceStatus status, String comment, UUID adminId) {
        validateSession(sessionType, cycleNumber);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        StudentAttendance record = attendanceRepository
                .findCell(studentId, sessionType, cycleNumber)
                .orElse(null);

        if (status == null) {
            if (record != null) {
                attendanceRepository.delete(record);
                attendanceRepository.flush();
            }
        } else {
            if (record == null) {
                record = StudentAttendance.builder()
                        .student(student)
                        .sessionType(sessionType)
                        .cycleNumber(cycleNumber)
                        .build();
            }
            record.setStatus(status);
            if (comment != null) {
                String trimmed = comment.trim();
                record.setComment(trimmed.isBlank() ? null : trimmed);
            }
            record.setUpdatedBy(adminId);
            record.setUpdatedAt(Instant.now());
            attendanceRepository.save(record);
            attendanceRepository.flush();
        }

        return buildStudentRow(student);
    }

    /**
     * Marks a whole group for one session at once ("mark all present" quick button),
     * so the teacher only has to click the few absent students afterwards.
     * A {@code null} default status resets every cell of the session.
     *
     * @return the recalculated attendance sheet of the group
     */
    @Transactional
    public List<StudentAttendanceRowDto> bulkMarkGroup(UUID groupId, SessionType sessionType, int cycleNumber,
                                                       AttendanceStatus defaultStatus, UUID adminId) {
        validateSession(sessionType, cycleNumber);
        List<Student> students = loadStudents(groupId, null);
        if (students.isEmpty()) {
            return List.of();
        }

        List<UUID> studentIds = students.stream().map(Student::getId).toList();
        Map<UUID, StudentAttendance> cells = attendanceRepository.findCells(studentIds, sessionType, cycleNumber)
                .stream()
                .collect(Collectors.toMap(
                        StudentAttendance::getStudentId,
                        record -> record,
                        (existing, replacement) -> existing
                ));

        List<StudentAttendance> toSave = new ArrayList<>();
        List<StudentAttendance> toDelete = new ArrayList<>();

        for (Student student : students) {
            StudentAttendance record = cells.get(student.getId());
            if (defaultStatus == null) {
                if (record != null) {
                    toDelete.add(record);
                }
                continue;
            }
            if (record == null) {
                record = StudentAttendance.builder()
                        .student(student)
                        .sessionType(sessionType)
                        .cycleNumber(cycleNumber)
                        .build();
            }
            record.setStatus(defaultStatus);
            record.setUpdatedBy(adminId);
            record.setUpdatedAt(Instant.now());
            toSave.add(record);
        }

        if (!toDelete.isEmpty()) {
            attendanceRepository.deleteAll(toDelete);
        }
        if (!toSave.isEmpty()) {
            attendanceRepository.saveAll(toSave);
        }
        attendanceRepository.flush();

        return getGroupAttendanceSheet(groupId);
    }

    private List<Student> loadStudents(UUID groupId, UUID streamId) {
        List<Student> students;
        if (groupId != null) {
            students = studentRepository.findByAcademicGroupId(groupId);
        } else if (streamId != null) {
            students = studentRepository.findByAcademicFlowId(streamId);
        } else {
            students = studentRepository.findAllWithAcademicGroup();
        }
        return new ArrayList<>(students);
    }

    private List<Student> loadStudents(UUID groupId) {
        return loadStudents(groupId, null);
    }

    private StudentAttendanceRowDto buildStudentRow(Student student) {
        List<Student> cohort = loadStudents(student.getGroupId());
        sortByFullName(cohort);

        int rowNumber = 1;
        for (int index = 0; index < cohort.size(); index++) {
            if (cohort.get(index).getId().equals(student.getId())) {
                rowNumber = index + 1;
                break;
            }
        }

        return toRow(student, rowNumber, attendanceRepository.findByStudentId(student.getId()));
    }

    private StudentAttendanceRowDto toRow(Student student, int rowNumber, List<StudentAttendance> records) {
        Map<Integer, AttendanceStatus> lectures = emptyCycleMap();
        Map<Integer, AttendanceStatus> practices = emptyCycleMap();
        Map<Integer, String> lectureComments = new LinkedHashMap<>();
        Map<Integer, String> practiceComments = new LinkedHashMap<>();

        for (StudentAttendance record : records) {
            if (record.getCycleNumber() == null || record.getStatus() == null) {
                continue;
            }
            int cycle = record.getCycleNumber();
            if (cycle < MIN_CYCLE_NUMBER || cycle > MAX_CYCLE_NUMBER) {
                continue;
            }
            boolean isLecture = record.getSessionType() == SessionType.LECTURE;
            Map<Integer, AttendanceStatus> target = isLecture ? lectures : practices;
            target.put(cycle, record.getStatus());

            if (record.getComment() != null && !record.getComment().isBlank()) {
                if (isLecture) {
                    lectureComments.put(cycle, record.getComment());
                } else {
                    practiceComments.put(cycle, record.getComment());
                }
            }
        }

        int lecturesPresent = (int) lectures.values().stream()
                .filter(status -> status == AttendanceStatus.PRESENT)
                .count();
        int practicesPresent = (int) practices.values().stream()
                .filter(status -> status == AttendanceStatus.PRESENT)
                .count();
        int excused = (int) Stream.concat(lectures.values().stream(), practices.values().stream())
                .filter(status -> status == AttendanceStatus.EXCUSED)
                .count();
        int absent = (int) Stream.concat(lectures.values().stream(), practices.values().stream())
                .filter(status -> status == AttendanceStatus.ABSENT)
                .count();

        int conducted = lecturesPresent + practicesPresent + excused + absent;
        int attended = lecturesPresent + practicesPresent;
        double percent = conducted == 0 ? 0.0 : Math.round(attended * 1000.0 / conducted) / 10.0;
        String groupName = student.getAcademicGroup() != null ? student.getAcademicGroup().getName() : null;

        return new StudentAttendanceRowDto(
                student.getId(),
                rowNumber,
                student.getPairNumber(),
                fullName(student),
                groupName,
                lectures,
                practices,
                lectureComments,
                practiceComments,
                lecturesPresent,
                practicesPresent,
                excused,
                percent
        );
    }

    private static Map<Integer, AttendanceStatus> emptyCycleMap() {
        Map<Integer, AttendanceStatus> cycles = new LinkedHashMap<>();
        for (int cycle = MIN_CYCLE_NUMBER; cycle <= MAX_CYCLE_NUMBER; cycle++) {
            cycles.put(cycle, null);
        }
        return cycles;
    }

    private static void sortByFullName(List<Student> students) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag(RUSSIAN_LOCALE));
        students.sort((first, second) -> collator.compare(fullName(first), fullName(second)));
    }

    private static String fullName(Student student) {
        return String.join(" ", Stream.of(student.getLastName(), student.getFirstName(), student.getMiddleName())
                .filter(part -> part != null && !part.isBlank())
                .toList());
    }

    private static void validateSession(SessionType sessionType, int cycleNumber) {
        if (sessionType == null) {
            throw new IllegalArgumentException("Session type is required");
        }
        if (cycleNumber < MIN_CYCLE_NUMBER || cycleNumber > MAX_CYCLE_NUMBER) {
            throw new IllegalArgumentException("Cycle number must be between "
                    + MIN_CYCLE_NUMBER + " and " + MAX_CYCLE_NUMBER);
        }
    }

}
