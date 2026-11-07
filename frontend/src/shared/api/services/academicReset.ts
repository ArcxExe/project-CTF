import { apiRequest, authTokenStorage } from "@/shared/api/client";

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

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

export const academicResetApi = {
  async resetSemester(payload: ResetRequestDto): Promise<ResetResultDto> {
    return await apiRequest<ResetResultDto>("/api/admin/academic/reset", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async downloadMetricsCsv(): Promise<void> {
    const token = authTokenStorage.get();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}/api/metrics/v2`, { headers });
    if (!response.ok) {
      throw new Error(`Ошибка загрузки ведомости: ${response.statusText}`);
    }

    const blob = await response.blob();
    const link = document.createElement("a");
    link.href = window.URL.createObjectURL(blob);
    link.download = "v2_metrics.csv";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(link.href);
  },
};

