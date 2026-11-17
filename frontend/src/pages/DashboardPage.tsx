import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { dashboardApi, type DashboardStats } from "@/shared/api/services/dashboard";
import { auditLogsApi, type AuditLog } from "@/shared/api/services/auditLogs";
import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Loader } from "@/shared/ui/Loader/Loader";
import "./pages.css";

export const DashboardPage = () => {
  const { push } = useToastStore();
  const [stats, setStats] = useState<DashboardStats>({
    registeredStudents: 0,
    publishedChallenges: 0,
    solvedChallenges: 0,
    attemptsCount: 0,
  });
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [isLoadingAllLogs, setIsLoadingAllLogs] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void dashboardApi
      .getStats()
      .then((data) => {
        setStats(data);
        if (Array.isArray(data.recentAuditLogs)) {
          setAuditLogs(
            data.recentAuditLogs.map((raw: any) => ({
              id: String(raw.id ?? ""),
              timestamp: String(raw.timestamp ?? ""),
              user: String(raw.user ?? raw.username ?? ""),
              role: String(raw.role ?? ""),
              action: String(raw.action ?? raw.actionType ?? ""),
              target: String(raw.target ?? raw.targetObject ?? ""),
              oldValue: raw.oldValue,
              newValue: raw.newValue,
              details: raw.details,
              ip: String(raw.ip ?? raw.ipAddress ?? ""),
            })),
          );
        }
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить dashboard",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const handleLoadAllAuditLogs = async () => {
    setIsLoadingAllLogs(true);
    try {
      const all = await auditLogsApi.getAll();
      setAuditLogs(all);
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить журнал аудита",
        variant: "error",
      });
    } finally {
      setIsLoadingAllLogs(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем dashboard..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Административная панель"
        subtitle="Сводка учебной CTF-платформы и быстрый переход к ключевым рабочим разделам."
        actions={<Badge tone="success">backend live</Badge>}
      />

      <div className="metric-grid">
        <Card>
          <div className="metric-card">
            <span className="muted">Профилей студентов в курсе</span>
            <strong>{stats.registeredStudents}</strong>
            <Badge tone="success">
              в ведомости · всего учётных записей: {stats.totalUsersCount ?? stats.registeredStudents}
            </Badge>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Заданий</span>
            <strong>{stats.publishedChallenges}</strong>
            <Badge tone="info">
              опубликовано · тестов: {stats.publishedTestsCount ?? 0}
            </Badge>
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
            <li>
              <Link to="/admin/students">Студенты и санкции (статусы)</Link>,{" "}
              <Link to="/admin/groups">Группы</Link>,{" "}
              <Link to="/admin/streams">Потоки</Link>,{" "}
              <Link to="/admin/labs">Лабораторные баллы</Link> и{" "}
              <Link to="/admin/attendance">Посещаемость</Link>
            </li>
            <li>
              <Link to="/admin/competitions">Соревнования</Link>,{" "}
              <Link to="/admin/tests">Тесты и КТ</Link>,{" "}
              <Link to="/admin/tasks">Категории и банк заданий</Link>
            </li>
            <li>
              <Link to="/admin/rating">Рейтинг, ведомость и ручные корректировки баллов</Link>,{" "}
              <Link to="/admin/promo-codes">Промокоды</Link> и{" "}
              <Link to="/admin/grading-scales">Шкалы оценивания</Link>
            </li>
          </ul>
        </Card>

        <Card>
          <h3>Контроль и выгрузки</h3>
          <ul className="page-list">
            <li>
              <Link to="/admin/analytics">Аналитика активности, успеваемости и качества заданий</Link>
            </li>
            <li>
              <a
                href="#dashboard-audit-log"
                onClick={(e) => {
                  e.preventDefault();
                  document.getElementById("dashboard-audit-log")?.scrollIntoView({ behavior: "smooth" });
                }}
              >
                Журнал действий для аудита (ниже на странице)
              </a>
            </li>
            <li>
              <Link to="/admin/progress">Отчёты и выгрузки по учебным потокам, группам и ведомости</Link>
            </li>
          </ul>
        </Card>
      </div>

      <Card>
        <div id="dashboard-audit-log" className="page-stack">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.75rem" }}>
            <div>
              <h3 style={{ margin: 0 }}>Журнал действий для аудита</h3>
              <p className="muted" style={{ margin: "0.25rem 0 0 0", fontSize: "0.85rem" }}>
                Последние события безопасности, входов, оценивания и изменений в системе.
              </p>
            </div>
            <Button
              variant="secondary"
              disabled={isLoadingAllLogs}
              onClick={() => void handleLoadAllAuditLogs()}
            >
              {isLoadingAllLogs ? "Загружаем..." : "Загрузить полный журнал"}
            </Button>
          </div>

          <DataTable
            columns={[
              {
                key: "timestamp",
                title: "Время",
                render: (row) => (row.timestamp ? new Date(row.timestamp).toLocaleString("ru-RU") : "—"),
              },
              { key: "user", title: "Пользователь", render: (row) => row.user || "—" },
              { key: "role", title: "Роль", render: (row) => row.role || "—" },
              { key: "action", title: "Действие", render: (row) => row.action || "—" },
              { key: "target", title: "Объект", render: (row) => row.target || "—" },
              {
                key: "newValue",
                title: "Подробности / Изменение",
                render: (row) =>
                  row.oldValue && row.newValue
                    ? `${row.oldValue} → ${row.newValue}`
                    : row.newValue || row.details || "—",
              },
            ]}
            rows={auditLogs}
          />
        </div>
      </Card>
    </div>
  );
};
