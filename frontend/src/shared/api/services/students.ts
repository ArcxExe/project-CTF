import { apiRequest, authTokenStorage } from "@/shared/api/client";
import type { Student } from "@/shared/types/education";

interface BackendStudentResponse {
  id: string;
  fullName: string;
  username: string;
  email: string;
  studentCode?: string;
  groupId?: string;
  groupName?: string;
  status?: "ACTIVE" | "BLOCKED" | "OUT_OF_RATING" | "DISQUALIFIED";
}

const statusMap: Record<NonNullable<BackendStudentResponse["status"]>, Student["status"]> = {
  ACTIVE: "active",
  BLOCKED: "blocked",
  OUT_OF_RATING: "out_of_rating",
  DISQUALIFIED: "disqualified",
};

const backendStatusMap: Record<Student["status"], string> = {
  active: "ACTIVE",
  blocked: "BLOCKED",
  out_of_rating: "OUT_OF_RATING",
  disqualified: "DISQUALIFIED",
};

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
  status: response.status ? statusMap[response.status] : "active",
});

export const studentsApi = {
  async getAll(): Promise<Student[]> {
    const response = await apiRequest<BackendStudentResponse[]>("/api/admin/students");
    return response.map(toStudent);
  },

  async importStudents(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await apiRequest<void>("/api/admin/students/import", {
      method: "POST",
      body: formData,
    });
  },

  async updateStatus(
    id: string,
    status: "active" | "blocked" | "out_of_rating" | "disqualified"
  ): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: backendStatusMap[status] }),
    });
  },

  async getPendingBindings(): Promise<Student[]> {
    const response = await apiRequest<BackendStudentResponse[]>(
      "/api/admin/students/pending-bindings"
    );
    return response.map(toStudent);
  },

  async approveBinding(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/approve-binding`, {
      method: "POST",
    });
  },

  async rejectBinding(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/reject-binding`, {
      method: "POST",
    });
  },
};

// Re-export authTokenStorage for blob download helpers in other modules
export { authTokenStorage };

