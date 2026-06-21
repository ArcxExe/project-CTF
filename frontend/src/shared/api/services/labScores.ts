import { apiRequest } from "@/shared/api/client";

export interface LabScoreRow {
  studentId: string;
  studentName: string;
  groupName: string;
  score: number;
  status: string;
}

export const labScoresApi = {
  async getAllScores(): Promise<LabScoreRow[]> {
    return await apiRequest<LabScoreRow[]>("/api/lab-scores");
  },

  async importScores(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);

    await apiRequest<void>("/api/lab-scores/import", {
      method: "POST",
      body: formData,
    });
  },

  async setScore(studentId: string, score: number, reason: string): Promise<void> {
    await apiRequest<void>("/api/lab-scores/adjust", {
      method: "POST",
      body: JSON.stringify({ studentId, score, reason }),
    });
  },
};
