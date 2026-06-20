import { apiRequest } from "@/shared/api/client";
import type { Group } from "@/shared/types/education";

/** Matches backend AcademicGroupResponseDTO (academic/group/dto/AcademicGroupResponseDTO.java) */
interface BackendGroupResponse {
  id: string;
  name: string;
  createdAt: string;
  streamId: string | null;
}

/** Matches backend AcademicGroupRequestDTO (academic/group/dto/AcademicGroupRequestDTO.java) */
export interface GroupPayload {
  name: string;
  streamId?: string;
}

const toGroup = (response: BackendGroupResponse): Group => ({
  id: response.id,
  name: response.name,
  streamId: response.streamId ?? undefined,
  createdAt: response.createdAt,
});

/**
 * Academic Groups API — /api/groups
 * Full CRUD operations for managing academic groups.
 */
export const groupsApi = {
  async getAll(): Promise<Group[]> {
    const response = await apiRequest<BackendGroupResponse[]>("/api/groups");
    return response.map(toGroup);
  },

  async getById(id: string): Promise<Group> {
    const response = await apiRequest<BackendGroupResponse>(`/api/groups/${id}`);
    return toGroup(response);
  },

  async create(payload: GroupPayload): Promise<Group> {
    const response = await apiRequest<BackendGroupResponse>("/api/groups", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return toGroup(response);
  },

  async update(id: string, payload: GroupPayload): Promise<Group> {
    const response = await apiRequest<BackendGroupResponse>(`/api/groups/${id}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    return toGroup(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/groups/${id}`, {
      method: "DELETE",
    });
  },
};
