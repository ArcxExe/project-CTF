import { create } from "zustand";
import { authApi } from "@/shared/api/services/auth";
import type { AuthPayload, User } from "@/shared/types/user";

interface AuthState {
  currentUser: User | null;
  isLoading: boolean;
  isSessionHydrated: boolean;
  login: (payload: AuthPayload) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
}

const SESSION_KEY = "ctf-session-user";

export const useAuthStore = create<AuthState>((set, get) => ({
  currentUser: null,
  isLoading: false,
  isSessionHydrated: false,

  login: async (payload) => {
    set({ isLoading: true });
    try {
      const user = await authApi.login(payload);
      localStorage.setItem(SESSION_KEY, user.id);
      set({ currentUser: user, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    localStorage.removeItem(SESSION_KEY);
    set({ currentUser: null, isSessionHydrated: true });
  },

  restoreSession: async () => {
    const { isSessionHydrated, isLoading } = get();
    if (isSessionHydrated || isLoading) {
      return;
    }

    set({ isLoading: true });

    try {
      const userId = localStorage.getItem(SESSION_KEY);
      if (!userId) {
        set({ currentUser: null });
        return;
      }

      const user = await authApi.getCurrentUser(userId);
      if (!user) {
        localStorage.removeItem(SESSION_KEY);
      }

      set({ currentUser: user });
    } finally {
      set({ isLoading: false, isSessionHydrated: true });
    }
  },
}));
