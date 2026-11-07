package com.arcx.ctfplatform.academic.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetResultDto {

    private int deletedStudents;
    private int deletedUsers;
    private int deletedGroups;
    private int deletedAttempts;
    private int deletedQuizAttempts;
    private int deletedLabRecords;
    private int deletedAttendanceRecords;
    private int resetControlPointRecords;
    private Instant timestamp;
}
