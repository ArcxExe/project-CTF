import { apiRequest } from "@/shared/api/client";
import type { Student } from "@/shared/types/education";

interface BackendStudentResponse {
  id: string;
  fullName: string;
  username: string;
  email: string;
  studentCode?: string;
  groupId?: string;
  groupName?: string;
  status?: string;
  laboratoryScore?: number;
}

const toStudent = (response: BackendStudentResponse): Student => ({
  id: response.id,
  fullName: response.fullName,
  nickname: response.username,
  email: response.email,
  studentCode: response.studentCode,
  groupId: response.groupId,
  group: response.groupName ?? "Без группы",
  stream: "Не назначен",
  laboratoryScore: response.laboratoryScore ?? 0,
  status: (response.status as any) ?? "active",
});

export const studentsApi = {
  async getAll(): Promise<Student[]> {
    const response = await apiRequest<BackendStudentResponse[]>("/api/admin/students");
    return response.map(toStudent);
  },

  async getPending(): Promise<Student[]> {
    const response = await apiRequest<BackendStudentResponse[]>("/api/admin/students/pending");
    return response.map(toStudent);
  },

  async approveStudent(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/approve`, { method: "POST" });
  },

  async rejectStudent(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/reject`, { method: "POST" });
  },

  async updateStatus(id: string, status: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    });
  },
};
