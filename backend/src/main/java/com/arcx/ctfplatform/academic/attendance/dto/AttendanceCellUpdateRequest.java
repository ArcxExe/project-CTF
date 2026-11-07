package com.arcx.ctfplatform.academic.attendance.dto;

import java.util.UUID;

import com.arcx.ctfplatform.academic.attendance.entity.AttendanceStatus;
import com.arcx.ctfplatform.academic.attendance.entity.SessionType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to update a single attendance cell (instant save on a table click).
 * A {@code null} status resets the cell (the stored mark is deleted).
 * A {@code null} comment keeps the previous comment untouched.
 */
public record AttendanceCellUpdateRequest(
        @NotNull UUID studentId,
        @NotNull SessionType sessionType,
        @NotNull @Min(1) @Max(8) Integer cycleNumber,
        AttendanceStatus status,
        @Size(max = 255, message = "Comment must not exceed 255 characters")
        String comment
) {
}

