import { apiRequest } from "@/shared/api/client";
import type { Competition } from "@/shared/types/competition";

interface BackendCompetitionResponse {
  id: string;
  title: string;
  description?: string;
  startsAt?: string;
  endsAt?: string;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "COMPLETED" | "ARCHIVED";
  active?: boolean;
  createdAt?: string;
}

export interface CompetitionPayload {
  title: string;
  description?: string;
  startsAt?: string;
  endsAt?: string;
  status?: Competition["status"];
}

const statusMap: Record<BackendCompetitionResponse["status"], Competition["status"]> = {
  DRAFT: "draft",
  PUBLISHED: "published",
  ACTIVE: "active",
  COMPLETED: "completed",
  ARCHIVED: "archived",
};

const backendStatusMap: Record<Competition["status"], BackendCompetitionResponse["status"]> = {
  draft: "DRAFT",
  published: "PUBLISHED",
  active: "ACTIVE",
  completed: "COMPLETED",
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
  createdAt: response.createdAt,
});

export const competitionsApi = {
  async getAll(): Promise<Competition[]> {
    const response = await apiRequest<BackendCompetitionResponse[]>("/api/competitions");
    return response.map(toCompetition);
  },
};

const toBackendPayload = (payload: CompetitionPayload) => ({
  title: payload.title,
  description: payload.description,
  startsAt: payload.startsAt,
  endsAt: payload.endsAt,
  status: payload.status ? backendStatusMap[payload.status] : undefined,
});

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
};
