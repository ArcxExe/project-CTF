import { apiRequest } from "@/shared/api/client";
import type { Competition } from "@/shared/types/competition";
import { toChallenge } from "./challenges";
import type { BackendChallengeResponse } from "./challenges";

/**
 * Matches backend CompetitionResponse (competitions/dto/CompetitionResponse.java).
 * Status enum: DRAFT | ACTIVE | COMPLETED (CompetitionStatus.java).
 */
interface BackendCompetitionResponse {
  id: string;
  title: string;
  description: string | null;
  startDate: string | null;
  endDate: string | null;
  status: "DRAFT" | "ACTIVE" | "COMPLETED";
  createdAt: string | null;
  sumTestPoints: boolean;
  leaderboardHidden: boolean;
  hiddenStudentIds: string[];
  tasks: BackendChallengeResponse[];
}

/**
 * Matches backend CompetitionRequest (competitions/dto/CompetitionRequest.java).
 */
export interface CompetitionPayload {
  title: string;
  description?: string;
  startsAt?: string;
  endsAt?: string;
  status: Competition["status"];
  sumTestPoints: boolean;
  leaderboardHidden: boolean;
  hiddenStudentIds: string[];
}

const statusMap: Record<BackendCompetitionResponse["status"], Competition["status"]> = {
  DRAFT: "draft",
  ACTIVE: "active",
  COMPLETED: "completed",
};

const backendStatusMap: Record<Competition["status"], BackendCompetitionResponse["status"]> = {
  draft: "DRAFT",
  published: "ACTIVE",
  archived: "COMPLETED",
  active: "ACTIVE",
  completed: "COMPLETED",
};

const toCompetition = (response: BackendCompetitionResponse): Competition => ({
  id: response.id,
  title: response.title,
  description: response.description ?? "",
  status: statusMap[response.status],
  startsAt: response.startDate ?? response.createdAt ?? new Date().toISOString(),
  endsAt: response.endDate ?? response.createdAt ?? new Date().toISOString(),
  ratingVisible: true,
  promoCodesEnabled: false,
  createdAt: response.createdAt ?? undefined,
  sumTestPoints: response.sumTestPoints,
  leaderboardHidden: response.leaderboardHidden,
  hiddenStudentIds: response.hiddenStudentIds || [],
  tasks: response.tasks ? response.tasks.map(toChallenge) : [],
});

const toBackendPayload = (payload: CompetitionPayload) => ({
  title: payload.title,
  description: payload.description,
  startDate: payload.startsAt,
  endDate: payload.endsAt,
  status: backendStatusMap[payload.status],
  sumTestPoints: payload.sumTestPoints,
  leaderboardHidden: payload.leaderboardHidden,
  hiddenStudentIds: payload.hiddenStudentIds,
});

/**
 * Public Competitions API — GET endpoints available to all authenticated users.
 */
export const competitionsApi = {
  async getAll(): Promise<Competition[]> {
    const response = await apiRequest<BackendCompetitionResponse[]>("/api/competitions");
    return response.map(toCompetition);
  },

  async getById(id: string): Promise<Competition> {
    const response = await apiRequest<BackendCompetitionResponse>(`/api/competitions/${id}`);
    return toCompetition(response);
  },
};

/**
 * Admin Competitions API — mutation endpoints.
 * Admin actions are routed to /api/admin/competitions.
 */
export const adminCompetitionsApi = {
  async getAll(): Promise<Competition[]> {
    const response = await apiRequest<BackendCompetitionResponse[]>("/api/admin/competitions");
    return response.map(toCompetition);
  },

  async create(payload: CompetitionPayload): Promise<Competition> {
    const response = await apiRequest<BackendCompetitionResponse>("/api/admin/competitions", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toCompetition(response);
  },

  async update(id: string, payload: CompetitionPayload): Promise<Competition> {
    const response = await apiRequest<BackendCompetitionResponse>(`/api/admin/competitions/${id}`, {
      method: "PUT",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toCompetition(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/competitions/${id}`, {
      method: "DELETE",
    });
  },

  async linkTasks(competitionId: string, taskIds: string[]): Promise<void> {
    await apiRequest<void>(`/api/admin/competitions/${competitionId}/tasks`, {
      method: "POST",
      body: JSON.stringify({ taskIds }),
    });
  },
};
