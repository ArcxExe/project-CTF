import { apiRequest, downloadApiFile } from "@/shared/api/client";

export type ResetMode = "PROGRESS_ONLY" | "FULL_SEMESTER_WIPE";

export interface ResetRequestDto {
  mode: ResetMode;
  streamId?: string | null;
  groupId?: string | null;
  confirmationPhrase: string;
}

export interface ResetResultDto {
  deletedStudents: number;
  deletedUsers: number;
  deletedGroups: number;
  deletedAttempts: number;
  deletedQuizAttempts: number;
  deletedLabRecords: number;
  deletedAttendanceRecords?: number;
  resetControlPointRecords?: number;
  timestamp: string;
}

export const academicResetApi = {
  async resetSemester(payload: ResetRequestDto): Promise<ResetResultDto> {
    return await apiRequest<ResetResultDto>("/api/admin/academic/reset", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async downloadMetricsCsv(): Promise<void> {
    await downloadApiFile("/api/metrics/v2", "v2_metrics.csv", "Ошибка загрузки ведомости");
  },
};

