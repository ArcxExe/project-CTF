import { apiRequest } from "@/shared/api/client";

export interface ScoreAdjustmentPayload {
  studentId: string;
  competitionId?: string;
  points: number;
  reason: string;
}

export interface ScoreAdjustmentResponse {
  id: string;
  studentId: string;
  studentName: string;
  username: string;
  competitionId: string | null;
  points: number;
  reason: string;
  createdAt: string;
}

export const scoreAdjustmentsApi = {
  async createAdjustment(payload: ScoreAdjustmentPayload): Promise<void> {
    await apiRequest<void>("/api/admin/score-adjustments", {
      method: "POST",
      body: JSON.stringify({
        studentId: payload.studentId,
        competitionId: payload.competitionId || null,
        points: payload.points,
        reason: payload.reason,
      }),
    });
  },

  async getAll(): Promise<ScoreAdjustmentResponse[]> {
    return await apiRequest<ScoreAdjustmentResponse[]>("/api/score-adjustments");
  },
};
