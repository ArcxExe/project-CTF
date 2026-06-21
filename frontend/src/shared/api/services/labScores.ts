import { apiRequest } from "@/shared/api/client";

export interface LabScore {
  id: string;
  studentName: string;
  groupName: string;
  score: number;
  status: string;
}

export const labScoresApi = {
  async getAllScores(): Promise<LabScore[]> {
    return apiRequest<LabScore[]>("/api/admin/lab-scores");
  },

  async setScore(studentId: string, score: number, reason: string): Promise<void> {
    await apiRequest<void>("/api/admin/lab-scores", {
      method: "POST",
      body: JSON.stringify({ studentId, score, reason }),
    });
  },

  async importScores(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await apiRequest<void>("/api/admin/lab-scores/import", {
      method: "POST",
      body: formData,
    });
  },
};
