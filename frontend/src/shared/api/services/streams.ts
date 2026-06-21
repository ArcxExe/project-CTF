import { apiRequest } from "@/shared/api/client";
import type { Stream } from "@/shared/types/education";

interface BackendStreamResponse {
  id: string;
  name: string;
  createdAt?: string;
  groupsCount?: number;
  studentsCount?: number;
}

export interface StreamPayload {
  name: string;
}

const toStream = (response: BackendStreamResponse): Stream => ({
  id: response.id,
  name: response.name,
  groupsCount: response.groupsCount || 0,
  studentsCount: response.studentsCount || 0,
  createdAt: response.createdAt,
});

export const streamsApi = {
  async getAll(): Promise<Stream[]> {
    const response = await apiRequest<BackendStreamResponse[]>("/api/streams");
    return response.map(toStream);
  },

  async create(payload: StreamPayload): Promise<Stream> {
    const response = await apiRequest<BackendStreamResponse>("/api/streams", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return toStream(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/streams/${id}`, {
      method: "DELETE",
    });
  },
};
