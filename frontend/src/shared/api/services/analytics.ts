import { apiRequest } from "@/shared/api/client";

export interface GroupMetrics {
  maxLabScore: number;
  maxCtfScore: number;
  avgLabScore: number;
  avgCtfScore: number;
  admissionPercentage: number;
  blockedOrDisqualifiedCount: number;
}

export interface StudentAnalytics {
  id: string;
  fullName: string;
  status: string;
  labScore: number;
  ctfScore: number;
  v1: number;
  v2: number;
  totalScore: number;
  totalScore100: number;
  recommendedGrade: string;
}

export interface AnalyticsSummary {
  metrics: GroupMetrics;
  students: StudentAnalytics[];
}

export interface TaskAnalytics {
  challengeId: string;
  challengeName: string;
  solvePercentage: number;
  firstBloodStudentName: string;
  firstBloodTime: string;
  incorrectAttempts: number;
}

export const analyticsApi = {
  async getGroupAnalytics(groupId?: string, flowId?: string): Promise<AnalyticsSummary> {
    const params = new URLSearchParams();
    if (groupId) params.append("groupId", groupId);
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/analytics/group${queryString ? "?" + queryString : ""}`;

    return apiRequest<AnalyticsSummary>(url);
  },

  async getTaskAnalytics(groupId?: string, flowId?: string): Promise<TaskAnalytics[]> {
    const params = new URLSearchParams();
    if (groupId) params.append("groupId", groupId);
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/analytics/tasks${queryString ? "?" + queryString : ""}`;

    return apiRequest<TaskAnalytics[]>(url);
  },
};
