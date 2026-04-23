import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Badge } from "@/shared/ui/Badge/Badge";
import "./pages.css";

export const DashboardPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Административная панель"
      subtitle="Стартовый экран для управления учебной CTF-платформой."
      actions={<Badge tone="info">mock mode</Badge>}
    />

    <div className="grid grid-2">
      <Card>
        <h3>Что уже можно делать</h3>
        <ul className="page-list">
          <li>Переключаться между ролями через mock login</li>
          <li>Открывать административные страницы</li>
          <li>Просматривать студентов и соревнования из mock API</li>
          <li>Тестировать тему, модалки и уведомления</li>
        </ul>
      </Card>

      <Card>
        <h3>Что подключать следующим шагом</h3>
        <ul className="page-list">
          <li>Экран тестового этапа</li>
          <li>Экран основного CTF</li>
          <li>Real-time рейтинг</li>
          <li>Реальные API-контракты</li>
        </ul>
      </Card>
    </div>
  </div>
);
