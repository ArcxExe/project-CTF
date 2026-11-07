import { apiRequest, authTokenStorage } from "@/shared/api/client";

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

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

async function downloadFile(url: string, fallbackFilename: string): Promise<void> {
  const token = authTokenStorage.get();
  const headers: Record<string, string> = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${url}`, { headers });
  if (!response.ok) {
    throw new Error(`Ошибка выгрузки файла: ${response.statusText}`);
  }

  const blob = await response.blob();
  const link = document.createElement("a");
  link.href = window.URL.createObjectURL(blob);
  link.download = fallbackFilename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(link.href);
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
    await downloadFile(`/api/gradebook/export/csv${qs ? `?${qs}` : ""}`, "Рейтинг.csv");
  },

  async exportXlsx(groupId?: string, flowId?: string): Promise<void> {
    const params = new URLSearchParams();
    if (groupId) params.set("groupId", groupId);
    if (flowId) params.set("flowId", flowId);
    const qs = params.toString();
    await downloadFile(`/api/gradebook/export/xlsx${qs ? `?${qs}` : ""}`, "Рейтинг.xlsx");
  },
};
