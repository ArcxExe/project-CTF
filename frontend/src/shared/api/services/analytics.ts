import { apiRequest } from "@/shared/api/client";

export interface GroupMetrics {
  maxLabScore: number;
  maxCtfScore: number;
  avgLabScore: number;
  avgCtfScore: number;
  admissionPercentage: number;
  blockedOrDisqualifiedCount: number;
  avgLabPercent?: number;
  avgKtScore?: number;
  avgKtPercent?: number;
  avgCtfPercent?: number;
  avgSScore?: number;
  labAdmissionPercentage?: number;
  avgAttendancePercent?: number;
  grade5Count?: number;
  grade4Count?: number;
  grade3Count?: number;
  grade2Count?: number;
}

export interface StudentAnalytics {
  id: string;
  fullName: string;
  fio?: string;
  pairNumber?: number | null;
  groupName?: string;
  status: string;
  labScore: number;
  exactLabScore?: number;
  acceptedLabsCount?: number;
  isEligible?: boolean;
  lPercent?: number;
  ktScore?: number;
  kPercent?: number;
  totalPoints?: number;
  ctfScore: number;
  ctfCoeff?: number;
  cPercent?: number;
  token?: string | null;
  v1: number;
  v2: number;
  totalScore: number;
  totalScore100: number;
  sScore?: number;
  recommendedGrade: string;
  numericGrade?: number;
  attendancePercent?: number;
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

export interface StudentTestAnalytics {
  studentId: string;
  studentName: string;
  groupName: string;
  testId: string;
  testTitle: string;
  status: "COMPLETED" | "IN_PROGRESS" | "NOT_STARTED";
  score: number;
  passingScore: number;
  date: string;
}

export interface StudentChallengeAnalytics {
  studentId: string;
  studentName: string;
  groupName: string;
  challengeId: string;
  challengeName: string;
  category: string;
  maxScore: number;
  solved: boolean;
  scoreAwarded: number;
  attemptsCount: number;
  date: string;
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

  async getStudentTestAnalytics(groupId?: string, flowId?: string): Promise<StudentTestAnalytics[]> {
    const params = new URLSearchParams();
    if (groupId) params.append("groupId", groupId);
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/analytics/student-tests${queryString ? "?" + queryString : ""}`;

    return apiRequest<StudentTestAnalytics[]>(url);
  },

  async getStudentChallengeAnalytics(groupId?: string, flowId?: string): Promise<StudentChallengeAnalytics[]> {
    const params = new URLSearchParams();
    if (groupId) params.append("groupId", groupId);
    if (flowId) params.append("flowId", flowId);

    const queryString = params.toString();
    const url = `/api/admin/analytics/student-challenges${queryString ? "?" + queryString : ""}`;

    return apiRequest<StudentChallengeAnalytics[]>(url);
  },
};
