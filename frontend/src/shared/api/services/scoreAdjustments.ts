import { apiRequest } from "@/shared/api/client";

export interface ScoreAdjustmentPayload {
  studentId: string;
  competitionId?: string;
  points: number;
  reason: string;
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
};
