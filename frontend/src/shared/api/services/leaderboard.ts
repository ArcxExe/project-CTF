import { apiRequest } from "@/shared/api/client";

export interface LeaderboardRow {
  place: number;
  participant: string;
  group: string;
  score: number;
  solved: number;
}

interface BackendLeaderboardEntry {
  studentId: string;
  username: string;
  groupName?: string;
  score: number;
  solvedCount: number;
}

export const leaderboardApi = {
  async getAll(): Promise<LeaderboardRow[]> {
    const response = await apiRequest<BackendLeaderboardEntry[]>("/api/leaderboard");

    return response.map((entry, index) => ({
      place: index + 1,
      participant: entry.username,
      group: entry.groupName ?? "Без группы",
      score: entry.score,
      solved: entry.solvedCount,
    }));
  },
};
