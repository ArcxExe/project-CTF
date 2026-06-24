import { create } from "zustand";
import { authTokenStorage } from "@/shared/api/client";
import { authApi } from "@/shared/api/services/auth";
import type { AuthPayload, User } from "@/shared/types/user";

interface AuthState {
  currentUser: User | null;
  isLoading: boolean;
  isSessionHydrated: boolean;
  login: (payload: AuthPayload) => Promise<void>;
  register: (payload: AuthPayload & { studentCode: string }) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
  updateUserScore: (scoreOffset: number) => void;
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

  logout: () => {
    authTokenStorage.clear();
    set({ currentUser: null, isSessionHydrated: true });
  },

  updateUserScore: (_scoreOffset) => {
    // Left unimplemented as we now refetch the leaderboard dynamically instead of saving it on the user object
    // Kept here for potential backward compatibility if other places call it, but no-op.
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
