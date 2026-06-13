import { create } from "zustand";
import { authTokenStorage } from "@/shared/api/client";
import { authApi } from "@/shared/api/services/auth";
import type { Role } from "@/shared/types/common";
import type { AuthPayload, User } from "@/shared/types/user";

interface AuthState {
  currentUser: User | null;
  isLoading: boolean;
  isSessionHydrated: boolean;
  login: (payload: AuthPayload) => Promise<void>;
  register: (payload: AuthPayload & { fullName?: string }) => Promise<void>;
  loginAsMockUser: (role: Role) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  currentUser: null,
  isLoading: false,
  isSessionHydrated: false,

  login: async (payload) => {
    set({ isLoading: true });
    try {
      const session = await authApi.login(payload);
      set({ currentUser: session.user, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (payload) => {
    set({ isLoading: true });
    try {
      const session = await authApi.register(payload);
      set({ currentUser: session.user, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  loginAsMockUser: async (role) => {
    set({ isLoading: true });
    try {
      const session = await authApi.loginAsRole(role);
      set({ currentUser: session.user, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    authTokenStorage.clear();
    set({ currentUser: null, isSessionHydrated: true });
  },

  restoreSession: async () => {
    const { isSessionHydrated, isLoading } = get();
    if (isSessionHydrated || isLoading) {
      return;
    }

    set({ isLoading: true });

    try {
      if (!authTokenStorage.get()) {
        set({ currentUser: null });
        return;
      }

      const user = await authApi.getCurrentUser();
      set({ currentUser: user });
    } catch {
      authTokenStorage.clear();
      set({ currentUser: null });
    } finally {
      set({ isLoading: false, isSessionHydrated: true });
    }
  },
}));
