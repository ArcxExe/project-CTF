package com.arcx.ctfplatform.academic.attendance.dto;

import java.util.Map;
import java.util.UUID;

import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;

/**
 * One row of the group attendance sheet (matrix view).
 *
 * @param studentId             student identifier
 * @param rowNumber             serial number in the sheet (students ordered by full name)
 * @param pairNumber            student's pair number, may be {@code null} for individual students
 * @param fullName              "Last First Middle" full name
 * @param groupName             academic group name
 * @param lectures              cycle number (1..8) to lecture status, {@code null} marks an untouched cell
 * @param practices             cycle number (1..8) to practice status, {@code null} marks an untouched cell
 * @param lecturesPresentCount  number of attended lectures
 * @param practicesPresentCount number of attended practices
 * @param excusedCount          number of excused absences (both lectures and practices)
 * @param attendancePercent     attended sessions percent of conducted (marked) sessions
 */
public record StudentAttendanceRowDto(
        UUID studentId,
        Integer rowNumber,
        Integer pairNumber,
        String fullName,
        String groupName,
        Map<Integer, AttendanceStatus> lectures,
        Map<Integer, AttendanceStatus> practices,
        Map<Integer, String> lectureComments,
        Map<Integer, String> practiceComments,
        int lecturesPresentCount,
        int practicesPresentCount,
        int excusedCount,
        double attendancePercent
) {
    public StudentAttendanceRowDto(
            UUID studentId,
            Integer rowNumber,
            Integer pairNumber,
            String fullName,
            String groupName,
            Map<Integer, AttendanceStatus> lectures,
            Map<Integer, AttendanceStatus> practices,
            int lecturesPresentCount,
            int practicesPresentCount,
            int excusedCount,
            double attendancePercent) {
        this(studentId, rowNumber, pairNumber, fullName, groupName,
                lectures, practices, Map.of(), Map.of(),
                lecturesPresentCount, practicesPresentCount, excusedCount, attendancePercent);
    }
}

