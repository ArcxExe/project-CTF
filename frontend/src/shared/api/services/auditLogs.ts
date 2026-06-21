import { apiRequest } from "@/shared/api/client";

export interface AuditLog {
  id: string;
  timestamp: string;
  username?: string;
  role?: string;
  actionType: string;
  targetObject?: string;
  oldValue?: string;
  newValue?: string;
  ipAddress?: string;
}

export const auditLogsApi = {
  async getAll(search?: string): Promise<AuditLog[]> {
    const url = search
      ? `/api/admin/audit-logs?search=${encodeURIComponent(search)}`
      : "/api/admin/audit-logs";
    return apiRequest<AuditLog[]>(url);
  },
};
