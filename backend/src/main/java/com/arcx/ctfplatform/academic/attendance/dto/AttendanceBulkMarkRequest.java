package com.arcx.ctfplatform.academic.attendance.dto;

import java.util.UUID;

import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;
import com.arcx.ctfplatform.academic.attendance.entity.SessionType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to mark a whole group at once for one session ("Mark all present" quick button).
 * A {@code null} default status resets every cell of the session.
 */
public record AttendanceBulkMarkRequest(
        @NotNull(message = "Group ID is required")
        UUID groupId,
        @NotNull SessionType sessionType,
        @NotNull @Min(1) @Max(8) Integer cycleNumber,
        AttendanceStatus defaultStatus
) {
}

