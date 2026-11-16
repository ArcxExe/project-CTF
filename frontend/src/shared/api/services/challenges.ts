import { apiRequest, authTokenStorage } from "@/shared/api/client";

/**
 * Frontend CTF Task model (formerly Challenge).
 */
export interface CtfTask {
  id: string;
  title: string;
  description: string;
  category?: string;
  difficulty?: "easy" | "medium" | "hard" | string;
  baseScore: number;
  maxScore?: number;
  isFirstBloodOnly?: boolean;
  points: number;
  createdAt: string;
  taskType?: "FLAG" | "FILE_UPLOAD" | string;
  attachments?: string[];
  taskCode?: string;
  bankId?: string;
  pool?: string;
  wave?: number;
}

export type Challenge = CtfTask;

/**
 * Payload for creating/updating a task.
 */
export interface ChallengePayload {
  title: string;
  description?: string;
  category?: string;
  difficulty?: string;
  baseScore: number;
  maxScore?: number;
  isFirstBloodOnly?: boolean;
  flag?: string;
  taskType?: "FLAG" | "FILE_UPLOAD" | string;
  attachments?: string[];
  taskCode?: string;
  bankId?: string;
  pool?: string;
  wave?: number;
  solutionInternal?: string;
}

/** Matches backend CtfTaskResponse (challenges/dto/CtfTaskResponse.java) */
export interface BackendChallengeResponse {
  id: string;
  title: string;
  description: string | null;
  category: string | null;
  difficulty: string | null;
  baseScore: number;
  maxScore?: number;
  isFirstBloodOnly?: boolean;
  createdAt: string;
  taskType?: "FLAG" | "FILE_UPLOAD" | string;
  attachments?: string[];
  taskCode?: string | null;
  bankId?: string | null;
  pool?: string | null;
  wave?: number | null;
}

export interface TaskImportResultDto {
  dryRun: boolean;
  createdCount: number;
  updatedCount: number;
  mainPoolCount: number;
  reservePoolCount: number;
  totalPoints: number;
  skippedRows: number;
  linkedToCompetitionCount: number;
  errors: string[];
  warnings: string[];
}

export interface SubmitFlagResponse {
  challengeId: string;
  correct: boolean;
  isCorrect?: boolean;
  message: string;
  pointsAdded: number;
}

export const toChallenge = (response: BackendChallengeResponse): Challenge => ({
  id: response.id,
  title: response.title,
  description: response.description ?? "",
  category: response.category ?? undefined,
  difficulty: response.difficulty ?? undefined,
  baseScore: response.baseScore,
  maxScore: response.maxScore ?? response.baseScore,
  isFirstBloodOnly: Boolean(response.isFirstBloodOnly),
  points: response.baseScore,
  createdAt: response.createdAt,
  taskType: response.taskType ?? "FLAG",
  attachments: response.attachments ?? [],
  taskCode: response.taskCode ?? undefined,
  bankId: response.bankId ?? "GLOBAL",
  pool: response.pool ?? "MAIN",
  wave: response.wave ?? undefined,
});

const toBackendPayload = (payload: ChallengePayload) => ({
  title: payload.title,
  description: payload.description,
  category: payload.category,
  difficulty: payload.difficulty,
  baseScore: payload.baseScore,
  maxScore: payload.maxScore ?? payload.baseScore,
  isFirstBloodOnly: Boolean(payload.isFirstBloodOnly),
  flag: payload.flag,
  taskType: payload.taskType ?? "FLAG",
  attachments: payload.attachments,
  taskCode: payload.taskCode,
  bankId: payload.bankId,
  pool: payload.pool,
  wave: payload.wave,
  solutionInternal: payload.solutionInternal,
});

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

async function downloadChallengeFile(url: string, fallbackFilename: string): Promise<void> {
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

/**
 * Public Challenges API — GET endpoints available to all authenticated users.
 */
export const challengesApi = {
  async getAll(): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>("/api/challenges");
    return response.map(toChallenge);
  },

  async getById(id: string): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>(`/api/challenges/${id}`);
    return toChallenge(response);
  },

  async submitFlag(id: string, flag: string): Promise<SubmitFlagResponse> {
    const res = await apiRequest<any>(`/api/challenges/${id}/submit`, {
      method: "POST",
      body: JSON.stringify({ flag }),
    });
    const correct = Boolean(res.correct ?? res.isCorrect);
    return {
      challengeId: String(res.challengeId ?? id),
      correct,
      isCorrect: correct,
      message: String(res.message ?? ""),
      pointsAdded: Number(res.pointsAdded ?? 0),
    };
  },

  async submitFile(challengeId: string, file: File): Promise<SubmitFlagResponse> {
    const formData = new FormData();
    formData.append("file", file);
    const res = await apiRequest<any>(`/api/challenges/${challengeId}/submit-file`, {
      method: "POST",
      body: formData,
    });
    const correct = Boolean(res.correct ?? res.isCorrect);
    return {
      challengeId: String(res.challengeId ?? challengeId),
      correct,
      isCorrect: correct,
      message: String(res.message ?? ""),
      pointsAdded: Number(res.pointsAdded ?? 0),
    };
  },
};

/**
 * Admin Challenges API — mutation endpoints protected on backend.
 */
export const adminChallengesApi = {
  async getAll(): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>("/api/challenges");
    return response.map(toChallenge);
  },

  async create(payload: ChallengePayload & { flag: string }): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>("/api/challenges", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toChallenge(response);
  },

  async update(id: string, payload: ChallengePayload): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>(`/api/challenges/${id}`, {
      method: "PUT",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toChallenge(response);
  },

  async getFlag(id: string): Promise<{ flag: string; solutionInternal?: string }> {
    return await apiRequest<{ flag: string; solutionInternal?: string }>(`/api/challenges/${id}/flag`);
  },

  async importTasks(
    file: File,
    options?: { dryRun?: boolean; competitionId?: string },
  ): Promise<TaskImportResultDto> {
    const formData = new FormData();
    formData.append("file", file);
    const params = new URLSearchParams();
    if (options?.dryRun) {
      params.set("dryRun", "true");
    }
    if (options?.competitionId) {
      params.set("competitionId", options.competitionId);
    }
    const qs = params.toString();
    return await apiRequest<TaskImportResultDto>(`/api/challenges/import${qs ? `?${qs}` : ""}`, {
      method: "POST",
      body: formData,
    });
  },

  async downloadTemplate(): Promise<void> {
    await downloadChallengeFile("/api/challenges/import-template", "ctf_tasks_template.xlsx");
  },

  async exportTasks(): Promise<void> {
    await downloadChallengeFile("/api/challenges/export", "ctf_tasks_bank.xlsx");
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/challenges/${id}`, {
      method: "DELETE",
    });
  },
};
