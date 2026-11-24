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

function parseOptionalGrade(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isInteger(value) && value >= 2 && value <= 5) {
    return value;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (/^[2-5]$/.test(trimmed)) {
      return Number(trimmed);
    }
  }
  return undefined;
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
    grade: parseOptionalGrade(raw.grade) ?? parseOptionalGrade(gradeName),
    description: raw.description ?? gradeName,
  };
}

export function toGradingScaleRequestBody(payload: GradingScalePayload) {
  const minScore = Number(payload.minScore ?? payload.minCoefficient ?? 0);
  const maxScore = Number(payload.maxScore ?? payload.maxCoefficient ?? 0);
  const gradeName = String(
    payload.gradeName ?? (payload.grade != null ? String(payload.grade) : (payload.description ?? ""))
  ).trim();
  const flowId = payload.flowId && payload.flowId.trim() ? payload.flowId.trim() : null;
  const grade = parseOptionalGrade(payload.grade) ?? parseOptionalGrade(gradeName);
  const description = payload.description ?? gradeName;

  return {
    flowId,
    gradeName,
    minScore,
    maxScore,
    minCoefficient: minScore,
    maxCoefficient: maxScore,
    ...(grade !== undefined ? { grade } : {}),
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
