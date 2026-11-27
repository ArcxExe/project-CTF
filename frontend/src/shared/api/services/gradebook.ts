import { apiRequest, downloadApiFile } from "@/shared/api/client";

export interface LabCellDto {
  labNumber: number;
  score: number;
  maxScore: number;
  rawScore: number;
  maxRawScore: number;
  defenseScore: number;
  maxDefenseScore: number;
  penaltyCoefficient: number;
  accepted: boolean;
}

export interface GradebookRowDto {
  studentId: string;
  rowNumber: number;
  pairNumber: number | null;
  fullName: string;
  studentCode: string;
  groupId: string | null;
  groupName: string;
  status: string;
  labs: Record<number, LabCellDto>;
  totalLab: number;
  acceptedLabsCount: number;
  isEligible: boolean;
  lPercent: number;
  kt1: number;
  kt2: number;
  kt3: number;
  kt4: number;
  totalKt: number;
  kPercent: number;
  totalPoints: number;
  ctfScore: number;
  ctfCoeff: number;
  cPercent: number;
  token: string | null;
  sScore: number;
  examScore: number | null;
  recommendedGrade: number | null;
  grade: number;
  maxKt1?: number;
  maxKt2?: number;
  maxKt3?: number;
  maxKt4?: number;
  maxTotalKt?: number;
  maxTotalPoints?: number;
}

export interface GradebookUpdateRequest {
  pairNumber?: number | null;
  labScores?: Record<number, number>;
  labAccepted?: Record<number, boolean>;
  kt1?: number;
  kt2?: number;
  kt3?: number;
  kt4?: number;
  token?: string;
  examScore?: number | null;
}

export interface LabDetailUpdateRequest {
  studentId: string;
  labNumber: number;
  rawScore: number;
  defenseScore: number;
  penaltyCoefficient: number;
  acceptedOverride?: boolean | null;
}

export const gradebookApi = {
  async getGradebook(groupId?: string, flowId?: string): Promise<GradebookRowDto[]> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (flowId) params.set("flowId", flowId);
    const qs = params.toString();
    return apiRequest<GradebookRowDto[]>(`/api/gradebook${qs ? `?${qs}` : ""}`);
  },

  async getMyGradebookRow(): Promise<GradebookRowDto | null> {
    try {
      return await apiRequest<GradebookRowDto>("/api/gradebook/me");
    } catch {
      return null;
    }
  },

  async updateStudentRow(studentId: string, payload: GradebookUpdateRequest): Promise<GradebookRowDto> {
    return apiRequest<GradebookRowDto>(`/api/gradebook/student/${studentId}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  async updateLabDetail(payload: LabDetailUpdateRequest): Promise<GradebookRowDto> {
    return apiRequest<GradebookRowDto>("/api/gradebook/lab-detail", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  async exportCsv(groupId?: string, flowId?: string): Promise<void> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (flowId) params.set("flowId", flowId);
    const qs = params.toString();
    await downloadApiFile(
      `/api/gradebook/export/csv${qs ? `?${qs}` : ""}`,
      "Рейтинг.csv",
      "Ошибка выгрузки ведомости"
    );
  },

  async exportXlsx(groupId?: string, flowId?: string): Promise<void> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (flowId) params.set("flowId", flowId);
    const qs = params.toString();
    await downloadApiFile(
      `/api/gradebook/export/xlsx${qs ? `?${qs}` : ""}`,
      "Рейтинг.xlsx",
      "Ошибка выгрузки ведомости"
    );
  },
};
