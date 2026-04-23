import { useEffect } from "react";
import { useThemeStore } from "@/features/theme/model/themeStore";
import { Button } from "@/shared/ui/Button/Button";

export const ThemeToggle = () => {
  const { theme, toggleTheme, syncTheme } = useThemeStore();

  useEffect(() => {
    syncTheme();
  }, [syncTheme]);

  return (
    <Button variant="ghost" onClick={toggleTheme}>
      Тема: {theme === "light" ? "светлая" : "тёмная"}
    </Button>
  );
};
