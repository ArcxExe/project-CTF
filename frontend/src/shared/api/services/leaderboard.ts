import { apiRequest } from "@/shared/api/client";

export interface LeaderboardRow {
  place: number;
  participant: string;
  group: string;
  score: number;
  solved: number;
  v1: number;
  v2: number;
  sCoefficient: number;
  recommendedGrade: number;
}

interface BackendLeaderboardEntry {
  studentId: string;
  username: string;
  groupName?: string;
  score: number;
  solvedCount: number;
  v1: number;
  v2: number;
  sCoefficient: number;
  recommendedGrade: number;
}

export const leaderboardApi = {
  async getAll(scopeType?: string, scopeId?: string): Promise<LeaderboardRow[]> {
    let url = "/api/leaderboard";
    if (scopeType && scopeId) {
      url += `?scopeType=${scopeType}&scopeId=${scopeId}`;
    }
    const response = await apiRequest<BackendLeaderboardEntry[]>(url);

    return response.map((entry, index) => ({
      place: index + 1,
      participant: entry.username,
      group: entry.groupName ?? "Без группы",
      score: entry.score,
      solved: entry.solvedCount,
      v1: entry.v1,
      v2: entry.v2,
      sCoefficient: entry.sCoefficient,
      recommendedGrade: entry.recommendedGrade,
    }));
  },
};
