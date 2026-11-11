import { apiRequest } from "@/shared/api/client";

export interface GradingScale {
  id: string;
  flowId?: string;
  gradeName: string;
  minScore: number;
  maxScore: number;
  minCoefficient?: number;
  maxCoefficient?: number;
  grade?: number;
  description?: string;
}

export type GradingScaleRule = GradingScale;

export interface GradingScalePayload {
  flowId?: string;
  gradeName: string;
  minScore: number;
  maxScore: number;
  minCoefficient?: number;
  maxCoefficient?: number;
  grade?: number;
  description?: string;
}

export function normalizeGradingScale(raw: any): GradingScale {
  const minScore = Number(raw.minScore ?? raw.minCoefficient ?? 0);
  const maxScore = Number(raw.maxScore ?? raw.maxCoefficient ?? 0);
  const gradeName = String(
    raw.gradeName ?? (raw.grade != null ? String(raw.grade) : (raw.description ?? ""))
  );
  const flowId = raw.flowId ?? "";

  return {
    id: String(raw.id ?? ""),
    flowId,
    gradeName,
    minScore,
    maxScore,
    minCoefficient: raw.minCoefficient ?? minScore,
    maxCoefficient: raw.maxCoefficient ?? maxScore,
    grade: raw.grade ?? (parseInt(gradeName, 10) || 5),
    description: raw.description ?? gradeName,
  };
}

export function toGradingScaleRequestBody(payload: GradingScalePayload) {
  const minScore = Number(payload.minScore ?? payload.minCoefficient ?? 0);
  const maxScore = Number(payload.maxScore ?? payload.maxCoefficient ?? 0);
  const gradeName = String(
    payload.gradeName ?? (payload.grade != null ? String(payload.grade) : (payload.description ?? ""))
  );
  const flowId = payload.flowId ?? "";
  const grade = payload.grade ?? (parseInt(String(gradeName), 10) || 5);
  const description = payload.description ?? gradeName;

  return {
    flowId,
    gradeName,
    minScore,
    maxScore,
    minCoefficient: minScore,
    maxCoefficient: maxScore,
    grade,
    description,
  };
}

export const gradingScalesApi = {
  async getAll(flowId?: string): Promise<GradingScale[]> {
    const params = new URLSearchParams();
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/grading-scales${queryString ? "?" + queryString : ""}`;

    const response = await apiRequest<any[]>(url);
    return (Array.isArray(response) ? response : []).map(normalizeGradingScale);
  },

  async create(payload: GradingScalePayload): Promise<GradingScale> {
    const response = await apiRequest<any>("/api/admin/grading-scales", {
      method: "POST",
      body: JSON.stringify(toGradingScaleRequestBody(payload)),
    });
    return normalizeGradingScale(response);
  },

  async update(id: string, payload: GradingScalePayload): Promise<GradingScale> {
    const response = await apiRequest<any>(`/api/admin/grading-scales/${id}`, {
      method: "PUT",
      body: JSON.stringify(toGradingScaleRequestBody(payload)),
    });
    return normalizeGradingScale(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/grading-scales/${id}`, {
      method: "DELETE",
    });
  },
};
