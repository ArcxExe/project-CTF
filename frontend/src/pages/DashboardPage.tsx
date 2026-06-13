import { useEffect, useState } from "react";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { dashboardApi } from "@/shared/api/services/dashboard";
import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Loader } from "@/shared/ui/Loader/Loader";
import "./pages.css";

export const DashboardPage = () => {
  const { push } = useToastStore();
  const [stats, setStats] = useState({
    registeredStudents: 0,
    publishedChallenges: 0,
    solvedChallenges: 0,
    attemptsCount: 0,
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void dashboardApi
      .getStats()
      .then(setStats)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить dashboard",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем dashboard..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Административная панель"
        subtitle="Сводка учебной CTF-платформы и быстрый контроль ключевых зон."
        actions={<Badge tone="success">backend live</Badge>}
      />

      <div className="metric-grid">
        <Card>
          <div className="metric-card">
            <span className="muted">Студентов</span>
            <strong>{stats.registeredStudents}</strong>
            <Badge tone="success">зарегистрировано</Badge>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Заданий</span>
            <strong>{stats.publishedChallenges}</strong>
            <Badge tone="info">опубликовано</Badge>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Попыток</span>
            <strong>{stats.attemptsCount}</strong>
            <Badge tone="neutral">{stats.solvedChallenges} решений</Badge>
          </div>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card>
          <h3>Операционные разделы</h3>
          <ul className="page-list">
            <li>Студенты, группы, потоки и лабораторные баллы</li>
            <li>Соревнования, тесты, категории и задания</li>
            <li>Ручные проверки, санкции, промокоды и рейтинг</li>
          </ul>
        </Card>

        <Card>
          <h3>Контроль и выгрузки</h3>
          <ul className="page-list">
            <li>Аналитика активности и качества заданий</li>
            <li>Журнал действий для аудита</li>
            <li>Отчеты по учебным потокам и соревнованиям</li>
          </ul>
        </Card>
      </div>
    </div>
  );
};
