import { apiRequest, downloadApiFile } from "@/shared/api/client";
import type {
  AttendanceBulkMarkPayload,
  AttendanceCellUpdatePayload,
  StudentAttendanceRow,
} from "@/shared/types/attendance";

/**
 * Attendance API — /api/academic/attendance
 * Matrix sheet of 8 lectures and 8 practices for the group attendance register.
 */
export const attendanceApi = {
  async getSheet(groupId?: string, streamId?: string): Promise<StudentAttendanceRow[]> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (streamId) params.set("streamId", streamId);
    const queryString = params.toString();
    const query = queryString ? `?${queryString}` : "";
    return apiRequest<StudentAttendanceRow[]>(`/api/academic/attendance/sheet${query}`);
  },

  async getMyAttendance(): Promise<StudentAttendanceRow | null> {
    try {
      return await apiRequest<StudentAttendanceRow>("/api/academic/attendance/me");
    } catch {
      return null;
    }
  },

  /** Instant save of a single matrix cell; returns the recalculated row. */
  async updateCell(payload: AttendanceCellUpdatePayload): Promise<StudentAttendanceRow> {
    return apiRequest<StudentAttendanceRow>("/api/academic/attendance/cell", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  /** Quick "mark all present" for one session of the whole group; returns the recalculated sheet. */
  async bulkMark(payload: AttendanceBulkMarkPayload): Promise<StudentAttendanceRow[]> {
    return apiRequest<StudentAttendanceRow[]>("/api/academic/attendance/bulk", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async exportCsv(groupId?: string, streamId?: string): Promise<void> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (streamId) params.set("streamId", streamId);
    const qs = params.toString();
    await downloadApiFile(
      `/api/academic/attendance/export/csv${qs ? `?${qs}` : ""}`,
      "Журнал_посещаемости.csv",
      "Ошибка выгрузки журнала посещаемости"
    );
  },

  async exportXlsx(groupId?: string, streamId?: string): Promise<void> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (streamId) params.set("streamId", streamId);
    const qs = params.toString();
    await downloadApiFile(
      `/api/academic/attendance/export/xlsx${qs ? `?${qs}` : ""}`,
      "Журнал_посещаемости.xlsx",
      "Ошибка выгрузки журнала посещаемости"
    );
  },
};
