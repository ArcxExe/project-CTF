import { apiRequest } from "@/shared/api/client";

export interface GradingScale {
  id: string;
  minCoefficient: number;
  maxCoefficient: number;
  grade: number;
  description?: string;
}

export interface GradingScalePayload {
  minCoefficient: number;
  maxCoefficient: number;
  grade: number;
  description?: string;
}

export const gradingScalesApi = {
  async getAll(): Promise<GradingScale[]> {
    return apiRequest<GradingScale[]>("/api/admin/grading-scales");
  },

  async create(payload: GradingScalePayload): Promise<GradingScale> {
    return apiRequest<GradingScale>("/api/admin/grading-scales", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async update(id: string, payload: GradingScalePayload): Promise<GradingScale> {
    return apiRequest<GradingScale>(`/api/admin/grading-scales/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/grading-scales/${id}`, {
      method: "DELETE",
    });
  },
};
