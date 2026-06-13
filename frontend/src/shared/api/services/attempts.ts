import { apiRequest } from "@/shared/api/client";

export interface AttemptHistoryEntry {
  attemptId: string;
  challengeId: string;
  challengeTitle: string;
  correct: boolean;
  earnedPoints: number;
  submittedAt: string;
}

export const attemptsApi = {
  getHistory(challengeId?: string): Promise<AttemptHistoryEntry[]> {
    const query = challengeId ? `?challengeId=${encodeURIComponent(challengeId)}` : "";
    return apiRequest<AttemptHistoryEntry[]>(`/api/attempts/history${query}`);
  },
};
