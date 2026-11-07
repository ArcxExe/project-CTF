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
}

export interface SubmitFlagResponse {
  challengeId: string;
  correct: boolean;
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
    return apiRequest<SubmitFlagResponse>(`/api/challenges/${id}/submit`, {
      method: "POST",
      body: JSON.stringify({ flag }),
    });
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
