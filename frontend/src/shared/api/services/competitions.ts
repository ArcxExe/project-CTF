import { apiRequest } from "@/shared/api/client";
import type { Competition } from "@/shared/types/competition";

/**
 * Matches backend CompetitionResponse (competitions/dto/CompetitionResponse.java).
 * Status enum: DRAFT | PUBLISHED | ARCHIVED (CompetitionStatus.java).
 */
interface BackendCompetitionResponse {
  id: string;
  title: string;
  description: string | null;
  startsAt: string | null;
  endsAt: string | null;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  createdAt: string | null;
  sumTestPoints: boolean;
  leaderboardHidden: boolean;
  hiddenStudentIds: string[];
}

/**
 * Matches backend CompetitionRequest (competitions/dto/CompetitionRequest.java).
 * title and status are @NotBlank / @NotNull on backend.
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
  PUBLISHED: "published",
  ARCHIVED: "archived",
};

const backendStatusMap: Record<Competition["status"], BackendCompetitionResponse["status"]> = {
  draft: "DRAFT",
  published: "PUBLISHED",
  archived: "ARCHIVED",
};

const toCompetition = (response: BackendCompetitionResponse): Competition => ({
  id: response.id,
  title: response.title,
  description: response.description ?? "",
  status: statusMap[response.status],
  startsAt: response.startsAt ?? response.createdAt ?? new Date().toISOString(),
  endsAt: response.endsAt ?? response.createdAt ?? new Date().toISOString(),
  ratingVisible: true,
  promoCodesEnabled: false,
  createdAt: response.createdAt ?? undefined,
  sumTestPoints: response.sumTestPoints,
  leaderboardHidden: response.leaderboardHidden,
  hiddenStudentIds: response.hiddenStudentIds || [],
});

const toBackendPayload = (payload: CompetitionPayload) => ({
  title: payload.title,
  description: payload.description,
  startsAt: payload.startsAt,
  endsAt: payload.endsAt,
  status: backendStatusMap[payload.status],
  sumTestPoints: payload.sumTestPoints,
  leaderboardHidden: payload.leaderboardHidden,
  hiddenStudentIds: payload.hiddenStudentIds,
});

/**
 * Public Competitions API — GET endpoints accessible to all authenticated users.
 * Backend filters results based on the authenticated user's role.
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
 * Admin Competitions API — mutation endpoints protected by @PreAuthorize("hasRole('ADMIN')") on backend.
 * All operations use the same /api/competitions base path (single controller).
 */
export const adminCompetitionsApi = {
  async getAll(): Promise<Competition[]> {
    const response = await apiRequest<BackendCompetitionResponse[]>("/api/competitions");
    return response.map(toCompetition);
  },

  async create(payload: CompetitionPayload): Promise<Competition> {
    const response = await apiRequest<BackendCompetitionResponse>("/api/competitions", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toCompetition(response);
  },

  async update(id: string, payload: CompetitionPayload): Promise<Competition> {
    const response = await apiRequest<BackendCompetitionResponse>(`/api/competitions/${id}`, {
      method: "PUT",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toCompetition(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/competitions/${id}`, {
      method: "DELETE",
    });
  },
};
