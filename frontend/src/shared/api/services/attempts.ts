import { apiRequest } from "@/shared/api/client";
import type { SubmitFlagResponse } from "./challenges";

export interface AttemptHistoryEntry {
  attemptId: string;
  challengeId: string;
  taskId?: string;
  challengeTitle: string;
  correct: boolean;
  isCorrect?: boolean;
  earnedPoints: number;
  submittedAt: string;
}

export interface Attempt {
  id: string;
  challengeId: string;
  taskId?: string;
  studentId: string;
  submittedFlag: string;
  isCorrect: boolean;
  correct?: boolean;
  filePath?: string;
  scoreAwarded?: number;
  submittedAt: string;
}

export interface GradeAttemptPayload {
  score: number;
  manualPercentMultiplier?: number;
  approved?: boolean;
}

export const attemptsApi = {
  async getHistory(challengeId?: string): Promise<AttemptHistoryEntry[]> {
    const query = challengeId
      ? `?taskId=${encodeURIComponent(challengeId)}&challengeId=${encodeURIComponent(challengeId)}`
      : "";
    const response = await apiRequest<any[]>(`/api/attempts/history${query}`);
    return (Array.isArray(response) ? response : []).map((item) => {
      const cId = String(item.challengeId ?? item.taskId ?? "");
      const isCorr = Boolean(item.correct ?? item.isCorrect);
      return {
        attemptId: String(item.attemptId ?? item.id ?? ""),
        challengeId: cId,
        taskId: cId,
        challengeTitle: String(item.challengeTitle ?? item.taskTitle ?? item.title ?? ""),
        correct: isCorr,
        isCorrect: isCorr,
        earnedPoints: Number(item.earnedPoints ?? item.scoreAwarded ?? 0),
        submittedAt: String(item.submittedAt ?? ""),
      };
    });
  },

  async submitFile(challengeId: string, file: File): Promise<SubmitFlagResponse> {
    const formData = new FormData();
    formData.append("file", file);
    const res = await apiRequest<any>(`/api/challenges/${challengeId}/submit-file`, {
      method: "POST",
      body: formData,
    });
    const correct = Boolean(res.correct ?? res.isCorrect);
    return {
      challengeId: String(res.challengeId ?? challengeId),
      correct,
      isCorrect: correct,
      message: String(res.message ?? ""),
      pointsAdded: Number(res.pointsAdded ?? 0),
    };
  },
};

export const adminAttemptsApi = {
  async getPendingReviews(): Promise<Attempt[]> {
    const response = await apiRequest<any[]>("/api/admin/attempts/pending-review");
    return (Array.isArray(response) ? response : []).map((a) => {
      const cId = String(a.challengeId ?? a.taskId ?? "");
      const isCorr = Boolean(a.isCorrect ?? a.correct);
      return {
        id: String(a.id ?? ""),
        challengeId: cId,
        taskId: cId,
        studentId: String(a.studentId ?? ""),
        submittedFlag: String(a.submittedFlag ?? ""),
        isCorrect: isCorr,
        correct: isCorr,
        filePath: a.filePath,
        scoreAwarded: a.scoreAwarded != null ? Number(a.scoreAwarded) : undefined,
        submittedAt: String(a.submittedAt ?? ""),
      };
    });
  },

  async gradeAttempt(id: string, payload: GradeAttemptPayload): Promise<void> {
    await apiRequest<void>(`/api/admin/attempts/${id}/grade`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
};
