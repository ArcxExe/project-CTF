import { apiRequest } from "@/shared/api/client";

export interface GradingScale {
  id: string;
  flowId?: string;
  gradeName: string;
  minScore: number;
  maxScore: number;
}

export interface GradingScalePayload {
  flowId?: string;
  gradeName: string;
  minScore: number;
  maxScore: number;
}

export const gradingScalesApi = {
  async getAll(flowId?: string): Promise<GradingScale[]> {
    const params = new URLSearchParams();
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/grading-scales${queryString ? "?" + queryString : ""}`;

    return apiRequest<GradingScale[]>(url);
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
