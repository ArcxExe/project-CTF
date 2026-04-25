import type { Identifier, ISODateTime, Role } from "@/shared/types/common";

export type ReportStatus = "queued" | "processing" | "ready" | "failed";
export type ReportFormat = "csv" | "xlsx" | "pdf" | "json";

export interface Report {
  id: Identifier;
  title: string;
  description?: string;
  format: ReportFormat;
  status: ReportStatus;
  filters?: Record<string, string | number | boolean | string[]>;
  fileUrl?: string;
  createdByUserId: Identifier;
  createdAt: ISODateTime;
  completedAt?: ISODateTime;
}

export interface AuditLog {
  id: Identifier;
  actorUserId?: Identifier;
  actorRole?: Role;
  action: string;
  entityType?: string;
  entityId?: Identifier;
  details?: Record<string, unknown>;
  ipAddress?: string;
  createdAt: ISODateTime;
}
