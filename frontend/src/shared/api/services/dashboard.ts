import { apiRequest } from "@/shared/api/client";

export interface DashboardStats {
  registeredStudents: number;
  publishedChallenges: number;
  solvedChallenges: number;
  attemptsCount: number;
  activeCompetitionsCount?: number;
  publishedTasksCount?: number;
  publishedTestsCount?: number;
  activePromoCodesCount?: number;
  totalUsersCount?: number;
  recentAuditLogs?: any[];
}

export const dashboardApi = {
  async getStats(): Promise<DashboardStats> {
    const raw = await apiRequest<any>("/api/admin/dashboard");
    return {
      registeredStudents: Number(raw.registeredStudents ?? raw.totalUsersCount ?? 0),
      publishedChallenges: Number(raw.publishedChallenges ?? raw.publishedTasksCount ?? 0),
      solvedChallenges: Number(raw.solvedChallenges ?? 0),
      attemptsCount: Number(raw.attemptsCount ?? 0),
      activeCompetitionsCount: raw.activeCompetitionsCount,
      publishedTasksCount: raw.publishedTasksCount,
      publishedTestsCount: raw.publishedTestsCount,
      activePromoCodesCount: raw.activePromoCodesCount,
      totalUsersCount: raw.totalUsersCount,
      recentAuditLogs: raw.recentAuditLogs,
    };
  },
};
