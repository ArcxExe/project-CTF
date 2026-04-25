import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/model/authStore";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import type { Role } from "@/shared/types/common";
import "./pages.css";

export const LoginPage = () => {
  const { currentUser, login, loginAsMockUser, isLoading } = useAuthStore();
  const { push } = useToastStore();
  const [email, setEmail] = useState("admin@ctf.local");
  const [password, setPassword] = useState("admin");

  if (currentUser) {
    const target = currentUser.role === "admin" ? "/admin/dashboard" : "/participant/profile";
    return <Navigate to={target} replace />;
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    try {
      await login({ email, password });
      push({ title: "Вход выполнен", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось войти",
        variant: "error",
      });
    }
  };

  const handleMockLogin = async (role: Role) => {
    try {
      await loginAsMockUser(role);
      push({ title: role === "admin" ? "Вход как админ выполнен" : "Вход как участник выполнен", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось войти через mock user",
        variant: "error",
      });
    }
  };

  return (
    <div className="auth-page">
      <Card className="auth-card">
        <div className="page-stack">
          <div>
            <h1>Вход в платформу</h1>
            <p className="muted">
              Тестовые учётные записи: admin@ctf.local / admin и student@ctf.local / student
            </p>
          </div>

          <form className="page-stack" onSubmit={handleSubmit}>
            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <Input
              label="Пароль"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <Button type="submit" fullWidth disabled={isLoading}>
              {isLoading ? "Входим..." : "Войти"}
            </Button>
          </form>

          <div className="mock-login-panel">
            <span className="muted">Быстрый вход без реального API</span>
            <div className="mock-login-panel__actions">
              <Button variant="secondary" onClick={() => void handleMockLogin("admin")} disabled={isLoading}>
                Войти как admin
              </Button>
              <Button
                variant="secondary"
                onClick={() => void handleMockLogin("participant")}
                disabled={isLoading}
              >
                Войти как participant
              </Button>
            </div>
          </div>

          <p className="auth-switch">
            Нет аккаунта? <Link to="/register">Зарегистрироваться</Link>
          </p>
        </div>
      </Card>
    </div>
  );
};
