import { create } from "zustand";
import { authTokenStorage, onUnauthorized } from "@/shared/api/client";
import { authApi, type RegisterPayload } from "@/shared/api/services/auth";
import type { AuthPayload, User } from "@/shared/types/user";

interface AuthState {
  currentUser: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSessionHydrated: boolean;
  login: (payload: AuthPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  currentUser: null,
  isAuthenticated: false,
  isLoading: false,
  isSessionHydrated: false,

  login: async (payload) => {
    set({ isLoading: true });
    try {
      const session = await authApi.login(payload);
      set({ currentUser: session.user, isAuthenticated: true, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (payload) => {
    set({ isLoading: true });
    try {
      const session = await authApi.register(payload);
      set({ currentUser: session.user, isAuthenticated: true, isLoading: false, isSessionHydrated: true });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: () => {
    authTokenStorage.clear();
    set({ currentUser: null, isAuthenticated: false, isSessionHydrated: true });
  },

  restoreSession: async () => {
    const { isSessionHydrated, isLoading } = get();
    if (isSessionHydrated || isLoading) {
      return;
    }

    set({ isLoading: true });

    try {
      if (!authTokenStorage.get()) {
        set({ currentUser: null, isAuthenticated: false });
        return;
      }

      const user = await authApi.getCurrentUser();
      set({ currentUser: user, isAuthenticated: true });
    } catch {
      authTokenStorage.clear();
      set({ currentUser: null, isAuthenticated: false });
    } finally {
      set({ isLoading: false, isSessionHydrated: true });
    }
  },
}));

onUnauthorized(() => {
  useAuthStore.setState({ currentUser: null, isAuthenticated: false, isSessionHydrated: true });
});

if (typeof window !== "undefined") {
  window.addEventListener("auth:unauthorized", () => {
    useAuthStore.setState({ currentUser: null, isAuthenticated: false, isSessionHydrated: true });
  });
}
