import { create } from "zustand";

type Theme = "light" | "dark";

interface ThemeState {
  theme: Theme;
  toggleTheme: () => void;
  syncTheme: () => void;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: "light",
  toggleTheme: () => {
    const next = get().theme === "light" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", next);
    set({ theme: next });
  },
  syncTheme: () => {
    document.documentElement.setAttribute("data-theme", get().theme);
  },
}));
