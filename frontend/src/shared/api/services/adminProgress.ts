import { apiRequest } from "@/shared/api/client";

export interface AdminProgressRow {
  participant: string;
  group: string;
  score: number;
  solved: number;
  attempts: number;
}

interface BackendAdminProgressRow {
  studentId: string;
  username: string;
  groupName?: string;
  totalScore: number;
  solvedCount: number;
  attemptsCount: number;
}

export const adminProgressApi = {
  async getAll(): Promise<AdminProgressRow[]> {
    const response = await apiRequest<BackendAdminProgressRow[]>("/api/admin/progress");
    return response.map((item) => ({
      participant: item.username,
      group: item.groupName ?? "Без группы",
      score: item.totalScore,
      solved: item.solvedCount,
      attempts: item.attemptsCount,
    }));
  },
};
