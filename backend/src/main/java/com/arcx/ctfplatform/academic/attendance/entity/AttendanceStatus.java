package com.arcx.ctfplatform.academic.attendance.entity;

/**
 * Attendance mark for a single session cell.
 * EXCUSED stands for a justified absence (regulation 3.4/7.3), e.g. a sick leave certificate.
 */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    EXCUSED
}
