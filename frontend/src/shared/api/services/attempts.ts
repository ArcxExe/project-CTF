import { apiRequest } from "@/shared/api/client";

export interface AttemptHistoryEntry {
  attemptId: string;
  challengeId: string;
  challengeTitle: string;
  correct: boolean;
  earnedPoints: number;
  submittedAt: string;
}

export interface Attempt {
  id: string;
  challengeId: string;
  studentId: string;
  submittedFlag: string;
  isCorrect: boolean;
  filePath?: string;
  scoreAwarded?: number;
  submittedAt: string;
}

export const attemptsApi = {
  getHistory(challengeId?: string): Promise<AttemptHistoryEntry[]> {
    const query = challengeId ? `?challengeId=${encodeURIComponent(challengeId)}` : "";
    return apiRequest<AttemptHistoryEntry[]>(`/api/attempts/history${query}`);
  },
};

export const adminAttemptsApi = {
  async getPendingReviews(): Promise<Attempt[]> {
    return apiRequest<Attempt[]>("/api/admin/attempts/pending-review");
  },

  async gradeAttempt(id: string, payload: { score: number, manualPercentMultiplier: number }): Promise<void> {
    await apiRequest<void>(`/api/admin/attempts/${id}/grade`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
};
