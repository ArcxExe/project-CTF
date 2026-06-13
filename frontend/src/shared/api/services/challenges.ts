import { apiRequest } from "@/shared/api/client";

export interface Challenge {
  id: string;
  competitionId: string;
  title: string;
  description: string;
  category: string;
  difficulty: "easy" | "medium" | "hard";
  points: number;
  status: "draft" | "published";
  createdAt: string;
}

export interface ChallengePayload {
  competitionId?: string;
  title: string;
  description?: string;
  category: string;
  difficulty: Challenge["difficulty"];
  points: number;
  flag?: string;
  status?: Challenge["status"];
}

export interface BackendChallengeResponse {
  id: string;
  competitionId: string;
  title: string;
  description?: string;
  category: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  points: number;
  status: "DRAFT" | "PUBLISHED";
  createdAt: string;
}

export interface SubmitFlagResponse {
  challengeId: string;
  correct: boolean;
  message: string;
  pointsAdded: number;
}

const difficultyMap: Record<BackendChallengeResponse["difficulty"], Challenge["difficulty"]> = {
  EASY: "easy",
  MEDIUM: "medium",
  HARD: "hard",
};

const statusMap: Record<BackendChallengeResponse["status"], Challenge["status"]> = {
  DRAFT: "draft",
  PUBLISHED: "published",
};

const backendDifficultyMap: Record<Challenge["difficulty"], BackendChallengeResponse["difficulty"]> = {
  easy: "EASY",
  medium: "MEDIUM",
  hard: "HARD",
};

const backendStatusMap: Record<Challenge["status"], BackendChallengeResponse["status"]> = {
  draft: "DRAFT",
  published: "PUBLISHED",
};

export const toChallenge = (response: BackendChallengeResponse): Challenge => ({
  id: response.id,
  competitionId: response.competitionId,
  title: response.title,
  description: response.description ?? "",
  category: response.category,
  difficulty: difficultyMap[response.difficulty],
  points: response.points,
  status: statusMap[response.status],
  createdAt: response.createdAt,
});

const toBackendPayload = (payload: ChallengePayload) => ({
  competitionId: payload.competitionId,
  title: payload.title,
  description: payload.description,
  category: payload.category,
  difficulty: backendDifficultyMap[payload.difficulty],
  points: payload.points,
  flag: payload.flag,
  status: payload.status ? backendStatusMap[payload.status] : undefined,
});

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

export const adminChallengesApi = {
  async getAll(): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>("/api/admin/challenges");
    return response.map(toChallenge);
  },

  async create(payload: ChallengePayload & { flag: string }): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>("/api/admin/challenges", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toChallenge(response);
  },

  async update(id: string, payload: ChallengePayload): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>(`/api/admin/challenges/${id}`, {
      method: "PUT",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toChallenge(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/challenges/${id}`, {
      method: "DELETE",
    });
  },

  async publish(id: string): Promise<Challenge> {
    const response = await apiRequest<BackendChallengeResponse>(`/api/admin/challenges/${id}/publish`, {
      method: "POST",
    });
    return toChallenge(response);
  },
};
