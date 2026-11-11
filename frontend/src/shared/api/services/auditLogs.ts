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
  details?: string;
  ip: string;
  username?: string;
  actionType?: string;
  targetObject?: string;
  ipAddress?: string;
}

interface RawAuditLog {
  id: string;
  timestamp?: string;
  user?: string;
  username?: string;
  role?: string;
  action?: string;
  actionType?: string;
  target?: string;
  targetObject?: string;
  oldValue?: string;
  newValue?: string;
  details?: string;
  ip?: string;
  ipAddress?: string;
}

export const auditLogsApi = {
  async getAll(): Promise<AuditLog[]> {
    const response = await apiRequest<any>("/api/audit-logs");
    const list: RawAuditLog[] = Array.isArray(response)
      ? response
      : (Array.isArray(response?.content) ? response.content : []);

    return list.map((raw) => {
      const user = String(raw.user ?? raw.username ?? "");
      const action = String(raw.action ?? raw.actionType ?? "");
      const target = String(raw.target ?? raw.targetObject ?? "");
      const ip = String(raw.ip ?? raw.ipAddress ?? "");
      const timestamp = String(raw.timestamp ?? "");

      return {
        id: String(raw.id ?? ""),
        timestamp,
        user,
        role: raw.role ?? "",
        action,
        target,
        oldValue: raw.oldValue,
        newValue: raw.newValue,
        details: raw.details,
        ip,
        username: raw.username ?? user,
        actionType: raw.actionType ?? action,
        targetObject: raw.targetObject ?? target,
        ipAddress: raw.ipAddress ?? ip,
      };
    });
  },
};
