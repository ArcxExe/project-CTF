import { apiRequest } from "@/shared/api/client";

export interface LabScore {
  studentId: string;
  studentCode: string;
  studentName: string;
  groupName: string;
  score: number;
  status: string;
  v1Coefficient: number;
}

export const labScoresApi = {
  async getAllScores(): Promise<LabScore[]> {
    return apiRequest<LabScore[]>("/api/admin/lab-scores");
  },

  async importScores(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await apiRequest<void>("/api/admin/lab-scores/import", {
      method: "POST",
      body: formData,
    });
  },

  async setScore(studentId: string, score: number, reason: string): Promise<void> {
    await apiRequest<void>("/api/admin/lab-scores", {
      method: "POST",
      body: JSON.stringify({ studentId, score, reason }),
    });
  },
};
