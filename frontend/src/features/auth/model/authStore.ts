import { create } from "zustand";
import { authApi } from "@/shared/api/services/auth";
import type { AuthPayload, User } from "@/shared/types/user";

interface AuthState {
  currentUser: User | null;
  isLoading: boolean;
  login: (payload: AuthPayload) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
}

const SESSION_KEY = "ctf-session-user";

export const useAuthStore = create<AuthState>((set) => ({
  currentUser: null,
  isLoading: false,

  login: async (payload) => {
    set({ isLoading: true });
    try {
      const user = await authApi.login(payload);
      localStorage.setItem(SESSION_KEY, user.id);
      set({ currentUser: user, isLoading: false });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    localStorage.removeItem(SESSION_KEY);
    set({ currentUser: null });
  },

  restoreSession: async () => {
    const userId = localStorage.getItem(SESSION_KEY);
    if (!userId) {
      return;
    }

    set({ isLoading: true });
    const user = await authApi.getCurrentUser(userId);
    set({ currentUser: user, isLoading: false });
  },
}));
