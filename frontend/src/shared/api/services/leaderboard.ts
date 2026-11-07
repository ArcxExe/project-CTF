import { apiRequest } from "@/shared/api/client";

export interface LeaderboardRow {
  place: number;
  participant: string;
  group: string;
  score: number;
  solved: number;
  lPercent: number;
  kPercent: number;
  cPercent: number;
  sScore: number;
  isEligible: boolean;
  recommendedGrade: number | null;
  v1?: number;
  v2?: number;
  sCoefficient?: number;
}

interface BackendLeaderboardEntry {
  studentId: string;
  username: string;
  groupName?: string;
  ctfScore: number;
  solvedCount: number;
  lPercent: number;
  kPercent: number;
  cPercent: number;
  sScore: number;
  recommendedGrade: number | null;
  isEligible: boolean;
  // deprecated fallback fields if any
  score?: number;
  v1?: number;
  v2?: number;
  sCoefficient?: number;
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
      score: entry.ctfScore ?? entry.score ?? 0,
      solved: entry.solvedCount ?? 0,
      lPercent: entry.lPercent ?? 0,
      kPercent: entry.kPercent ?? 0,
      cPercent: entry.cPercent ?? 0,
      sScore: entry.sScore ?? 0,
      isEligible: entry.isEligible ?? false,
      recommendedGrade: entry.recommendedGrade,
      v1: entry.lPercent ?? entry.v1 ?? 0,
      v2: entry.cPercent ?? entry.v2 ?? 0,
      sCoefficient: entry.sScore ?? entry.sCoefficient ?? 0,
    }));
  },
};
