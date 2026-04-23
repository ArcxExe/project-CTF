import type { Identifier, Role } from "@/shared/types/common";

export interface User {
  id: Identifier;
  fullName: string;
  email: string;
  role: Role;
  isBlocked: boolean;
}

export interface AuthPayload {
  email: string;
  password: string;
}
