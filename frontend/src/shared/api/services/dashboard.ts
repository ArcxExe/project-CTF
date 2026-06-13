import { apiRequest } from "@/shared/api/client";

export interface DashboardStats {
  registeredStudents: number;
  publishedChallenges: number;
  solvedChallenges: number;
  attemptsCount: number;
}

export const dashboardApi = {
  getStats(): Promise<DashboardStats> {
    return apiRequest<DashboardStats>("/api/admin/dashboard");
  },
};
