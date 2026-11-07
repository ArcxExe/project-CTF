package com.arcx.ctfplatform.academic.attendance.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arcx.ctfplatform.academic.attendance.dto.AttendanceBulkMarkRequest;
import com.arcx.ctfplatform.academic.attendance.dto.AttendanceCellUpdateRequest;
import com.arcx.ctfplatform.academic.attendance.dto.StudentAttendanceRowDto;
import com.arcx.ctfplatform.academic.attendance.service.AttendanceService;
import com.arcx.ctfplatform.users.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/academic/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * Attendance sheet of an academic group (all students when {@code groupId} is omitted).
     */
    @GetMapping("/sheet")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentAttendanceRowDto>> getSheet(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID streamId) {
        return ResponseEntity.ok(attendanceService.getGroupAttendanceSheet(groupId, streamId));
    }

    /**
     * Student's own attendance record for the 8 lectures and 8 practices.
     */
    @GetMapping("/me")
    public ResponseEntity<StudentAttendanceRowDto> getMyAttendance(
            @AuthenticationPrincipal User user) {
        UUID userId = user != null ? user.getId() : null;
        return attendanceService.getStudentAttendanceByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Instant save of a single cell when the admin clicks in the matrix.
     */
    @PutMapping("/cell")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<StudentAttendanceRowDto> updateCell(
            @Valid @RequestBody AttendanceCellUpdateRequest request,
            @AuthenticationPrincipal User user) {
        StudentAttendanceRowDto row = attendanceService.updateCell(
                request.studentId(),
                request.sessionType(),
                request.cycleNumber(),
                request.status(),
                request.comment(),
                user != null ? user.getId() : null);
        return ResponseEntity.ok(row);
    }

    /**
     * Quick "mark all present" for one session of a whole group.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentAttendanceRowDto>> bulkMark(
            @Valid @RequestBody AttendanceBulkMarkRequest request,
            @AuthenticationPrincipal User user) {
        List<StudentAttendanceRowDto> sheet = attendanceService.bulkMarkGroup(
                request.groupId(),
                request.sessionType(),
                request.cycleNumber(),
                request.defaultStatus(),
                user != null ? user.getId() : null);
        return ResponseEntity.ok(sheet);
    }

    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID streamId) {
        byte[] csv = attendanceService.exportAttendanceCsv(groupId, streamId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance_sheet.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/export/xlsx")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID streamId) {
        byte[] xlsx = attendanceService.exportAttendanceXlsx(groupId, streamId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance_sheet.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
