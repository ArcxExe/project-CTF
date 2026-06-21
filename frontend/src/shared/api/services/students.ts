import { apiRequest, authTokenStorage } from "@/shared/api/client";
import type { Student } from "@/shared/types/education";

interface BackendStudentResponse {
  id: string;
  firstName?: string;
  lastName?: string;
  middleName?: string;
  fullName?: string;
  username?: string;
  email?: string;
  studentCode?: string;
  groupId?: string;
  groupName?: string;
  status?: string;
  laboratoryScore?: number;
}

const statusMap: Record<string, Student["status"]> = {
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

const toStudent = (response: BackendStudentResponse): Student => {
  const first = response.firstName ?? "";
  const last = response.lastName ?? "";
  const calculatedFullName = response.fullName ?? [first, last].filter(Boolean).join(" ");

  return {
    id: response.id,
    fullName: calculatedFullName || "Студент",
    nickname: response.username ?? "",
    email: response.email ?? "",
    studentCode: response.studentCode,
    groupId: response.groupId,
    group: response.groupName ?? "Без группы",
    stream: "Не назначен",
    laboratoryScore: response.laboratoryScore ?? 0,
    status: response.status ? (statusMap[response.status] ?? (response.status.toLowerCase() as any)) : "active",
  };
};

// Helper to extract array from Spring Page or raw array response
function extractContent(response: any): BackendStudentResponse[] {
  if (Array.isArray(response)) {
    return response;
  }
  if (response && Array.isArray(response.content)) {
    return response.content;
  }
  return [];
}


export interface CreateStudentPayload {
  firstName: string;
  lastName: string;
  middleName?: string;
  studentCode: string;
  groupId: string;
}

export const studentsApi = {
  async createStudent(payload: CreateStudentPayload): Promise<void> {
    await apiRequest<void>("/api/admin/students", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async getAll(): Promise<Student[]> {
    const response = await apiRequest<any>("/api/admin/students");
    return extractContent(response).map(toStudent);
  },

  async importStudents(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await apiRequest<void>("/api/admin/students/import", {
      method: "POST",
      body: formData,
    });
  },

  async updateStatus(id: string, status: string): Promise<void> {
    const backendStatus = backendStatusMap[status as Student["status"]] ?? status.toUpperCase();
    await apiRequest<void>(`/api/admin/students/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: backendStatus }),
    });
  },

  async getPending(): Promise<Student[]> {
    const response = await apiRequest<any>("/api/admin/students/pending-bindings");
    return extractContent(response).map(toStudent);
  },

  async approveStudent(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/approve-binding`, {
      method: "POST",
    });
  },

  async rejectStudent(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/students/${id}/reject-binding`, {
      method: "POST",
    });
  },

  // Aliases for compatibility
  async getPendingBindings(): Promise<Student[]> {
    return this.getPending();
  },

  async approveBinding(id: string): Promise<void> {
    return this.approveStudent(id);
  },

  async rejectBinding(id: string): Promise<void> {
    return this.rejectStudent(id);
  },
};

export { authTokenStorage };
