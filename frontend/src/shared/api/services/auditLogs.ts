import { apiRequest } from "@/shared/api/client";

export interface AuditLog {
  id: string;
  timestamp: string;
  user: string;
  role: string;
  action: string;
  target: string;
  oldValue?: string;
  newValue?: string;
  ip: string;
}

export const auditLogsApi = {
  async getAll(): Promise<AuditLog[]> {
    return await apiRequest<AuditLog[]>("/api/audit-logs");
  },
};
