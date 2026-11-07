export type SessionType = "LECTURE" | "PRACTICE";

export type AttendanceStatus = "PRESENT" | "ABSENT" | "EXCUSED";

/** One row of the group attendance sheet (matrix view), matches backend StudentAttendanceRowDto. */
export interface StudentAttendanceRow {
  studentId: string;
  /** Serial number in the sheet (students ordered by full name). */
  rowNumber: number;
  /** Student pair number, null when the student works individually. */
  pairNumber: number | null;
  fullName: string;
  groupName: string | null;
  /** Cycle number (1..8, stringified keys in JSON) to lecture status; null marks an untouched cell. */
  lectures: Record<string, AttendanceStatus | null>;
  /** Cycle number (1..8, stringified keys in JSON) to practice status; null marks an untouched cell. */
  practices: Record<string, AttendanceStatus | null>;
  /** Cycle number to lecture comment, if any. */
  lectureComments?: Record<string, string>;
  /** Cycle number to practice comment, if any. */
  practiceComments?: Record<string, string>;
  lecturesPresentCount: number;
  practicesPresentCount: number;
  excusedCount: number;
  attendancePercent: number;
}

export interface AttendanceCellUpdatePayload {
  studentId: string;
  sessionType: SessionType;
  cycleNumber: number;
  /** null resets the cell. */
  status: AttendanceStatus | null;
  /** Omit to keep the previously stored comment untouched. */
  comment?: string;
}

export interface AttendanceBulkMarkPayload {
  groupId: string;
  sessionType: SessionType;
  cycleNumber: number;
  /** null resets every cell of the session. */
  defaultStatus: AttendanceStatus | null;
}


export const ATTENDANCE_CYCLES = [1, 2, 3, 4, 5, 6, 7, 8] as const;
