import { useEffect } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { ThemeToggle } from "@/features/theme/ui/ThemeToggle";
import { useAuthStore } from "@/features/auth/model/authStore";
import { Button } from "@/shared/ui/Button/Button";
import "./AppLayout.css";

const adminLinks = [
  { to: "/admin/dashboard", label: "Дашборд" },
  { to: "/admin/students", label: "Студенты" },
  { to: "/admin/competitions", label: "Соревнования" },
];

const participantLinks = [{ to: "/participant/profile", label: "Профиль" }];

export const AppLayout = () => {
  const { currentUser, restoreSession, logout } = useAuthStore();

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  const links = currentUser?.role === "admin" ? adminLinks : participantLinks;

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">
          <strong>CTF Platform</strong>
          <span>Frontend foundation</span>
        </div>

        <nav className="app-nav">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) => `app-nav__link ${isActive ? "is-active" : ""}`}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="app-main">
        <header className="app-topbar">
          <div>
            <div className="muted">Текущий пользователь</div>
            <strong>{currentUser?.fullName ?? "Гость"}</strong>
          </div>

          <div className="app-topbar__actions">
            <ThemeToggle />
            <Button variant="secondary" onClick={logout}>
              Выйти
            </Button>
          </div>
        </header>

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
