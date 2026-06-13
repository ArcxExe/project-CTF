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
  laboratoryScore: 0,
  status: "active",
});

export const studentsApi = {
  async getAll(): Promise<Student[]> {
    const response = await apiRequest<BackendStudentResponse[]>("/api/admin/students");
    return response.map(toStudent);
  },
};
