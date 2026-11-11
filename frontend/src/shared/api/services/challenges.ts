import { apiRequest } from "@/shared/api/client";

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
});

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

  async getFlag(id: string): Promise<{ flag: string }> {
    return await apiRequest<{ flag: string }>(`/api/challenges/${id}/flag`);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/challenges/${id}`, {
      method: "DELETE",
    });
  },
};
