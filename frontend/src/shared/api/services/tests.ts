import { apiRequest } from "@/shared/api/client";
import { toChallenge } from "@/shared/api/services/challenges";
import type { BackendChallengeResponse, Challenge, SubmitFlagResponse } from "@/shared/api/services/challenges";

export interface CtfTest {
  id: string;
  competitionId?: string;
  title: string;
  description: string;
  status: "draft" | "published" | "archived";
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CtfTestPayload {
  competitionId?: string;
  title: string;
  description?: string;
  status?: CtfTest["status"];
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
}

interface BackendTestResponse {
  id: string;
  competitionId?: string;
  title: string;
  description?: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
  createdAt: string;
  updatedAt: string;
}

const statusMap: Record<BackendTestResponse["status"], CtfTest["status"]> = {
  DRAFT: "draft",
  PUBLISHED: "published",
  ARCHIVED: "archived",
};

const backendStatusMap: Record<CtfTest["status"], BackendTestResponse["status"]> = {
  draft: "DRAFT",
  published: "PUBLISHED",
  archived: "ARCHIVED",
};

const toTest = (response: BackendTestResponse): CtfTest => ({
  id: response.id,
  competitionId: response.competitionId,
  title: response.title,
  description: response.description ?? "",
  status: statusMap[response.status],
  timeLimitMinutes: response.timeLimitMinutes,
  passingScore: response.passingScore,
  questionsCount: response.questionsCount,
  createdAt: response.createdAt,
  updatedAt: response.updatedAt,
});

const toBackendPayload = (payload: CtfTestPayload) => ({
  competitionId: payload.competitionId || undefined,
  title: payload.title,
  description: payload.description,
  status: payload.status ? backendStatusMap[payload.status] : undefined,
  timeLimitMinutes: payload.timeLimitMinutes,
  passingScore: payload.passingScore,
  questionsCount: payload.questionsCount,
});

export const testsApi = {
  async getPublished(): Promise<CtfTest[]> {
    const response = await apiRequest<BackendTestResponse[]>("/api/tests");
    return response.map(toTest);
  },

  async getChallenges(testId: string): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>(`/api/tests/${testId}/challenges`);
    return response.map(toChallenge);
  },

  async submitChallengeFlag(testId: string, challengeId: string, flag: string): Promise<SubmitFlagResponse> {
    return apiRequest<SubmitFlagResponse>(`/api/tests/${testId}/challenges/${challengeId}/submit`, {
      method: "POST",
      body: JSON.stringify({ flag }),
    });
  },
};

export const adminTestsApi = {
  async getAll(): Promise<CtfTest[]> {
    const response = await apiRequest<BackendTestResponse[]>("/api/admin/tests");
    return response.map(toTest);
  },

  async create(payload: CtfTestPayload): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>("/api/admin/tests", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toTest(response);
  },

  async getChallenges(testId: string): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>(`/api/admin/tests/${testId}/challenges`);
    return response.map(toChallenge);
  },

  async addChallenge(testId: string, challengeId: string): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>(`/api/admin/tests/${testId}/challenges/${challengeId}`, {
      method: "POST",
      body: JSON.stringify({}),
    });
    return toTest(response);
  },

  async removeChallenge(testId: string, challengeId: string): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>(`/api/admin/tests/${testId}/challenges/${challengeId}`, {
      method: "DELETE",
    });
    return toTest(response);
  },
};
