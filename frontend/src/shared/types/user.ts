import type { Identifier, ISODateTime, Role } from "@/shared/types/common";

export type UserStatus = "active" | "blocked" | "pending_verification";

export interface User {
  id: Identifier;
  fullName: string;
  username?: string;
  email: string;
  role: Role;
  isBlocked: boolean;
  status?: UserStatus;
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
}

export interface AuthPayload {
  email: string;
  password: string;
}
