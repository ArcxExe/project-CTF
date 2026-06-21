import { apiRequest, authTokenStorage } from "@/shared/api/client";
import type { Role } from "@/shared/types/common";
import type { AuthPayload, User } from "@/shared/types/user";

/** Matches backend AuthResponse record (auth/dto/AuthResponse.java) */
interface BackendAuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInMillis: number;
  userId: string;
  email: string;
  username?: string;
  role: "ADMIN" | "INSTRUCTOR" | "STUDENT" | "USER";
}

/** Matches expected /api/users/me response (not yet implemented on backend) */
interface BackendUserResponse {
  id: string;
  email: string;
  username: string;
  fullName?: string;
  role: "ADMIN" | "INSTRUCTOR" | "STUDENT" | "USER";
  status: "ACTIVE" | "BLOCKED" | "PENDING_VERIFICATION";
}

interface AuthSession {
  token: string;
  user: User;
}

/**
 * Backend roles → frontend roles mapping.
 * ADMIN and INSTRUCTOR both map to "admin" (full access).
 * STUDENT and USER both map to "participant" (limited access).
 */
const roleMap: Record<BackendAuthResponse["role"], Role> = {
  ADMIN: "admin",
  INSTRUCTOR: "admin",
  STUDENT: "participant",
  USER: "participant",
};

const statusMap: Record<BackendUserResponse["status"], User["status"]> = {
  ACTIVE: "active",
  BLOCKED: "blocked",
  PENDING_VERIFICATION: "pending_verification",
};

const toUser = (response: BackendUserResponse): User => ({
  id: response.id,
  fullName: response.fullName ?? response.username ?? response.email,
  username: response.username,
  email: response.email,
  role: roleMap[response.role],
  isBlocked: response.status === "BLOCKED",
  status: statusMap[response.status],
});

const toUserFromAuth = (response: BackendAuthResponse): User => ({
  id: response.userId,
  fullName: response.username ?? response.email,
  username: response.username,
  email: response.email,
  role: roleMap[response.role],
  isBlocked: false,
  status: "active",
});

const demoCredentials: Record<Role, AuthPayload> = {
  admin: { email: "admin@ctf.local", password: "password" },
  participant: { email: "student@ctf.local", password: "password" },
};

export const authApi = {
  async login(payload: AuthPayload): Promise<AuthSession> {
    const auth = await apiRequest<BackendAuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        login: payload.email,
        password: payload.password,
      }),
    });

    authTokenStorage.set(auth.accessToken);

    try {
      return {
        token: auth.accessToken,
        user: await this.getCurrentUser(),
      };
    } catch {
      return {
        token: auth.accessToken,
        user: toUserFromAuth(auth),
      };
    }
  },

  async register(payload: AuthPayload & { fullName?: string }): Promise<AuthSession> {
    const auth = await apiRequest<BackendAuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: payload.email,
        password: payload.password,
        fullName: payload.fullName,
      }),
    });

    authTokenStorage.set(auth.accessToken);

    try {
      return {
        token: auth.accessToken,
        user: await this.getCurrentUser(),
      };
    } catch {
      return {
        token: auth.accessToken,
        user: toUserFromAuth(auth),
      };
    }
  },

  async loginAsRole(role: Role): Promise<AuthSession> {
    return this.login(demoCredentials[role]);
  },

  async getCurrentUser(): Promise<User> {
    const response = await apiRequest<BackendUserResponse>("/api/users/me");
    return toUser(response);
  },
};
