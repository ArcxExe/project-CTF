import { apiRequest } from "@/shared/api/client";

/**
 * Frontend Challenge model.
 * Fields category, difficulty, status are kept optional for UI backward-compatibility
 * but are NOT present in the backend ChallengeResponse.
 */
export interface Challenge {
  id: string;
  competitionId: string;
  title: string;
  description: string;
  category?: string;
  difficulty?: "easy" | "medium" | "hard";
  points: number;
  status?: "draft" | "published";
  createdAt: string;
}

/**
 * Payload for creating/updating a challenge.
 * Matches backend ChallengeRequest: title, description, points, flag, competitionId.
 */
export interface ChallengePayload {
  competitionId?: string;
  title: string;
  description?: string;
  points: number;
  flag?: string;
}

/** Matches backend ChallengeResponse (challenges/dto/ChallengeResponse.java) */
export interface BackendChallengeResponse {
  id: string;
  title: string;
  description: string | null;
  points: number;
  competitionId: string;
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
  competitionId: response.competitionId,
  title: response.title,
  description: response.description ?? "",
  points: response.points,
  createdAt: response.createdAt,
});

const toBackendPayload = (payload: ChallengePayload) => ({
  title: payload.title,
  description: payload.description,
  points: payload.points,
  flag: payload.flag,
  competitionId: payload.competitionId,
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
 * Admin Challenges API — mutation endpoints protected by @PreAuthorize("hasRole('ADMIN')") on backend.
 * Uses the same /api/challenges base path (single controller in backend).
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

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/challenges/${id}`, {
      method: "DELETE",
    });
  },
};
