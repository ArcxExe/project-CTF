import { NavLink, Outlet } from "react-router-dom";
import { ThemeToggle } from "@/features/theme/ui/ThemeToggle";
import { useAuthStore } from "@/features/auth/model/authStore";
import { Button } from "@/shared/ui/Button/Button";
import "./AppLayout.css";

interface LayoutLink {
  to: string;
  label: string;
}

const adminLinks: LayoutLink[] = [
  { to: "/admin/dashboard", label: "Dashboard" },
  { to: "/admin/students", label: "Студенты" },
  { to: "/admin/groups", label: "Группы" },
  { to: "/admin/streams", label: "Потоки" },
  { to: "/admin/lab-scores", label: "Баллы за лабораторные" },
  { to: "/admin/competitions", label: "Соревнования" },
  { to: "/admin/tests", label: "Тесты" },
  { to: "/admin/categories", label: "Категории" },
  { to: "/admin/tasks", label: "Задания" },
  { to: "/admin/manual-reviews", label: "Ручные проверки" },
  { to: "/admin/rating", label: "Рейтинг" },
  { to: "/admin/promo-codes", label: "Промокоды" },
  { to: "/admin/sanctions", label: "Санкции" },
  { to: "/admin/analytics", label: "Аналитика" },
  { to: "/admin/action-log", label: "Журнал действий" },
  { to: "/admin/reports", label: "Отчеты" },
];

const participantLinks: LayoutLink[] = [
  { to: "/participant/profile", label: "Профиль" },
  { to: "/participant/competition", label: "Соревнование" },
  { to: "/participant/test", label: "Тест" },
  { to: "/participant/ctf", label: "CTF" },
  { to: "/participant/rating", label: "Рейтинг" },
  { to: "/participant/promo-code", label: "Промокод" },
];

interface RoleLayoutProps {
  links: LayoutLink[];
  navTitle: string;
  roleLabel: string;
}

const RoleLayout = ({ links, navTitle, roleLabel }: RoleLayoutProps) => {
  const { currentUser, logout } = useAuthStore();

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">
          <strong>CTF Platform</strong>
          <span>{roleLabel}</span>
        </div>

        <nav className="app-nav">
          <span className="app-nav__caption">{navTitle}</span>
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

export const AdminLayout = () => (
  <RoleLayout links={adminLinks} navTitle="Администрирование" roleLabel="Admin workspace" />
);

export const ParticipantLayout = () => (
  <RoleLayout links={participantLinks} navTitle="Участник" roleLabel="Participant workspace" />
);
