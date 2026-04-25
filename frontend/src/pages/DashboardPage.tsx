import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Badge } from "@/shared/ui/Badge/Badge";
import "./pages.css";

export const DashboardPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Административная панель"
      subtitle="Сводка учебной CTF-платформы и быстрый контроль ключевых зон."
      actions={<Badge tone="info">mock mode</Badge>}
    />

    <div className="metric-grid">
      <Card>
        <div className="metric-card">
          <span className="muted">Студентов</span>
          <strong>62</strong>
          <Badge tone="success">58 в рейтинге</Badge>
        </div>
      </Card>
      <Card>
        <div className="metric-card">
          <span className="muted">Соревнований</span>
          <strong>4</strong>
          <Badge tone="info">1 активное</Badge>
        </div>
      </Card>
      <Card>
        <div className="metric-card">
          <span className="muted">Ручные проверки</span>
          <strong>14</strong>
          <Badge tone="danger">очередь</Badge>
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
