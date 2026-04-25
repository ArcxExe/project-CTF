import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

type AdminRow = Record<string, string | number | boolean>;

interface AdminSectionConfig {
  title: string;
  subtitle: string;
  action: string;
  metrics: Array<{ label: string; value: string; tone?: "neutral" | "success" | "danger" | "info" }>;
  columns: Array<{ key: string; title: string }>;
  rows: AdminRow[];
}

const statusTone = (value: string): "neutral" | "success" | "danger" | "info" => {
  if (["Активно", "Опубликовано", "Готово", "Принято", "В рейтинге"].includes(value)) {
    return "success";
  }

  if (["Заблокирован", "Просрочено", "Отклонено", "Санкция"].includes(value)) {
    return "danger";
  }

  if (["Черновик", "Проверка", "Ожидает"].includes(value)) {
    return "info";
  }

  return "neutral";
};

const sectionConfigs: Record<string, AdminSectionConfig> = {
  groups: {
    title: "Группы",
    subtitle: "Учебные группы, привязка к потокам и количество студентов.",
    action: "Добавить группу",
    metrics: [
      { label: "Всего групп", value: "8" },
      { label: "С активными студентами", value: "7", tone: "success" },
      { label: "Без потока", value: "1", tone: "info" },
    ],
    columns: [
      { key: "name", title: "Группа" },
      { key: "stream", title: "Поток" },
      { key: "students", title: "Студентов" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { name: "ИВТ-21", stream: "Поток A", students: 24, status: "Активно" },
      { name: "ИБ-22", stream: "Поток B", students: 19, status: "Активно" },
      { name: "ПИ-23", stream: "Не назначен", students: 0, status: "Черновик" },
    ],
  },
  streams: {
    title: "Потоки",
    subtitle: "Потоки обучения для распределения групп, тестов и соревнований.",
    action: "Создать поток",
    metrics: [
      { label: "Потоков", value: "3" },
      { label: "Групп внутри", value: "8" },
      { label: "Средний прогресс", value: "68%", tone: "success" },
    ],
    columns: [
      { key: "name", title: "Поток" },
      { key: "owner", title: "Ответственный" },
      { key: "groups", title: "Группы" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { name: "Поток A", owner: "Кафедра ИВТ", groups: 3, status: "Активно" },
      { name: "Поток B", owner: "Кафедра ИБ", groups: 4, status: "Активно" },
      { name: "Весенний резерв", owner: "Администратор", groups: 1, status: "Черновик" },
    ],
  },
  labScores: {
    title: "Баллы за лабораторные",
    subtitle: "Учет лабораторных баллов, которые влияют на общий рейтинг участника.",
    action: "Импорт баллов",
    metrics: [
      { label: "Средний балл", value: "83" },
      { label: "Ниже порога", value: "5", tone: "danger" },
      { label: "Обновлено сегодня", value: "36", tone: "success" },
    ],
    columns: [
      { key: "student", title: "Студент" },
      { key: "group", title: "Группа" },
      { key: "score", title: "Баллы" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { student: "Иван Петров", group: "ИВТ-21", score: 82, status: "В рейтинге" },
      { student: "Мария Соколова", group: "ИВТ-21", score: 91, status: "В рейтинге" },
      { student: "Алексей Смирнов", group: "ИБ-22", score: 54, status: "Ожидает" },
    ],
  },
  tests: {
    title: "Тесты",
    subtitle: "Предварительные тесты допуска и контрольные блоки перед CTF.",
    action: "Создать тест",
    metrics: [
      { label: "Тестов", value: "6" },
      { label: "Опубликовано", value: "4", tone: "success" },
      { label: "Средний проход", value: "72%" },
    ],
    columns: [
      { key: "title", title: "Название" },
      { key: "questions", title: "Вопросов" },
      { key: "timeLimit", title: "Лимит" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { title: "Web basics", questions: 18, timeLimit: "25 мин", status: "Опубликовано" },
      { title: "Linux warmup", questions: 12, timeLimit: "20 мин", status: "Опубликовано" },
      { title: "Crypto entry", questions: 10, timeLimit: "15 мин", status: "Черновик" },
    ],
  },
  categories: {
    title: "Категории",
    subtitle: "Категории заданий, вес в соревновании и порядок отображения.",
    action: "Добавить категорию",
    metrics: [
      { label: "Категорий", value: "7" },
      { label: "С задачами", value: "6", tone: "success" },
      { label: "Пустые", value: "1", tone: "info" },
    ],
    columns: [
      { key: "name", title: "Категория" },
      { key: "tasks", title: "Заданий" },
      { key: "weight", title: "Вес" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { name: "Web", tasks: 12, weight: "30%", status: "Активно" },
      { name: "Crypto", tasks: 8, weight: "20%", status: "Активно" },
      { name: "Reverse", tasks: 0, weight: "10%", status: "Черновик" },
    ],
  },
  tasks: {
    title: "Задания",
    subtitle: "Банк CTF-заданий, стоимость, категория и состояние публикации.",
    action: "Создать задание",
    metrics: [
      { label: "Заданий", value: "42" },
      { label: "Опубликовано", value: "28", tone: "success" },
      { label: "На проверке", value: "6", tone: "info" },
    ],
    columns: [
      { key: "title", title: "Задание" },
      { key: "category", title: "Категория" },
      { key: "points", title: "Баллы" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { title: "Warmup Crypto", category: "Crypto", points: 100, status: "Опубликовано" },
      { title: "Cookie Jar", category: "Web", points: 250, status: "Проверка" },
      { title: "Lost Log", category: "Forensics", points: 300, status: "Черновик" },
    ],
  },
  manualReviews: {
    title: "Ручные проверки",
    subtitle: "Очередь флагов, отчетов и спорных решений, требующих решения администратора.",
    action: "Назначить проверяющего",
    metrics: [
      { label: "В очереди", value: "14", tone: "info" },
      { label: "Принято", value: "38", tone: "success" },
      { label: "Отклонено", value: "7", tone: "danger" },
    ],
    columns: [
      { key: "item", title: "Объект" },
      { key: "student", title: "Студент" },
      { key: "reviewer", title: "Проверяющий" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { item: "Write-up Lost Log", student: "rootfox", reviewer: "Не назначен", status: "Ожидает" },
      { item: "Флаг Cookie Jar", student: "bytebird", reviewer: "admin", status: "Принято" },
      { item: "Апелляция Reverse 100", student: "nmapkid", reviewer: "admin", status: "Отклонено" },
    ],
  },
  rating: {
    title: "Рейтинг",
    subtitle: "Административный вид рейтинга с учетом лабораторных, CTF и санкций.",
    action: "Экспорт рейтинга",
    metrics: [
      { label: "Участников", value: "62" },
      { label: "В рейтинге", value: "58", tone: "success" },
      { label: "Исключены", value: "4", tone: "danger" },
    ],
    columns: [
      { key: "place", title: "Место" },
      { key: "student", title: "Студент" },
      { key: "score", title: "Баллы" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { place: 1, student: "Мария Соколова", score: 920, status: "В рейтинге" },
      { place: 2, student: "Иван Петров", score: 760, status: "В рейтинге" },
      { place: 3, student: "Алексей Смирнов", score: 610, status: "Санкция" },
    ],
  },
  promoCodes: {
    title: "Промокоды",
    subtitle: "Промокоды для бонусов, подсказок и специальных условий соревнования.",
    action: "Создать промокод",
    metrics: [
      { label: "Активных", value: "12", tone: "success" },
      { label: "Использований", value: "86" },
      { label: "Просрочено", value: "3", tone: "danger" },
    ],
    columns: [
      { key: "code", title: "Код" },
      { key: "bonus", title: "Бонус" },
      { key: "uses", title: "Использовано" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { code: "SPRING-CTF", bonus: "+50 баллов", uses: "18/30", status: "Активно" },
      { code: "HINT-ONE", bonus: "Подсказка", uses: "42/100", status: "Активно" },
      { code: "OLD-DEMO", bonus: "+1 попытка", uses: "9/10", status: "Просрочено" },
    ],
  },
  sanctions: {
    title: "Санкции",
    subtitle: "Ограничения участников: блокировки, исключение из рейтинга и история причин.",
    action: "Добавить санкцию",
    metrics: [
      { label: "Активных", value: "4", tone: "danger" },
      { label: "Снято", value: "11", tone: "success" },
      { label: "На рассмотрении", value: "2", tone: "info" },
    ],
    columns: [
      { key: "student", title: "Студент" },
      { key: "type", title: "Тип" },
      { key: "reason", title: "Причина" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { student: "Алексей Смирнов", type: "Без рейтинга", reason: "Апелляция", status: "Санкция" },
      { student: "test-user", type: "Блокировка", reason: "Флуд", status: "Санкция" },
      { student: "rootfox", type: "Предупреждение", reason: "Проверка", status: "Ожидает" },
    ],
  },
  actionLog: {
    title: "Журнал действий",
    subtitle: "Аудит действий администраторов, участников и системных процессов.",
    action: "Скачать лог",
    metrics: [
      { label: "Событий сегодня", value: "248" },
      { label: "Ошибок", value: "3", tone: "danger" },
      { label: "Auth-событий", value: "61", tone: "info" },
    ],
    columns: [
      { key: "time", title: "Время" },
      { key: "actor", title: "Субъект" },
      { key: "action", title: "Действие" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { time: "10:32", actor: "admin", action: "TASK_PUBLISHED", status: "Готово" },
      { time: "10:28", actor: "rootfox", action: "FLAG_SUBMITTED", status: "Ожидает" },
      { time: "10:21", actor: "system", action: "SEED_DATA_LOADED", status: "Готово" },
    ],
  },
  reports: {
    title: "Отчеты",
    subtitle: "Выгрузки по студентам, соревнованиям, лабораторным и активности.",
    action: "Сформировать отчет",
    metrics: [
      { label: "Шаблонов", value: "9" },
      { label: "Готово", value: "5", tone: "success" },
      { label: "В очереди", value: "2", tone: "info" },
    ],
    columns: [
      { key: "name", title: "Отчет" },
      { key: "period", title: "Период" },
      { key: "format", title: "Формат" },
      { key: "status", title: "Статус" },
    ],
    rows: [
      { name: "Итоговый рейтинг", period: "Апрель", format: "XLSX", status: "Готово" },
      { name: "Попытки по заданиям", period: "Неделя", format: "CSV", status: "Готово" },
      { name: "Активность потоков", period: "Семестр", format: "PDF", status: "Ожидает" },
    ],
  },
};

const AdminSectionPage = ({ config }: { config: AdminSectionConfig }) => (
  <div className="page-stack">
    <PageHeader title={config.title} subtitle={config.subtitle} actions={<Button>{config.action}</Button>} />

    <div className="metric-grid">
      {config.metrics.map((metric) => (
        <Card key={metric.label}>
          <div className="metric-card">
            <span className="muted">{metric.label}</span>
            <strong>{metric.value}</strong>
            {metric.tone && <Badge tone={metric.tone}>status</Badge>}
          </div>
        </Card>
      ))}
    </div>

    <Card>
      <div className="admin-toolbar">
        <Input label="Поиск" placeholder={`Найти в разделе "${config.title}"`} />
        <Button variant="secondary">Фильтры</Button>
      </div>
    </Card>

    <DataTable
      columns={config.columns.map((column) => ({
        ...column,
        render:
          column.key === "status"
            ? (row: AdminRow) => <Badge tone={statusTone(String(row.status))}>{String(row.status)}</Badge>
            : undefined,
      }))}
      rows={config.rows}
    />
  </div>
);

export const AdminGroupsPage = () => <AdminSectionPage config={sectionConfigs.groups} />;
export const AdminStreamsPage = () => <AdminSectionPage config={sectionConfigs.streams} />;
export const AdminLabScoresPage = () => <AdminSectionPage config={sectionConfigs.labScores} />;
export const AdminTestsPage = () => <AdminSectionPage config={sectionConfigs.tests} />;
export const AdminCategoriesPage = () => <AdminSectionPage config={sectionConfigs.categories} />;
export const AdminTasksPage = () => <AdminSectionPage config={sectionConfigs.tasks} />;
export const AdminManualReviewsPage = () => <AdminSectionPage config={sectionConfigs.manualReviews} />;
export const AdminRatingPage = () => <AdminSectionPage config={sectionConfigs.rating} />;
export const AdminPromoCodesPage = () => <AdminSectionPage config={sectionConfigs.promoCodes} />;
export const AdminSanctionsPage = () => <AdminSectionPage config={sectionConfigs.sanctions} />;
export const AdminActionLogPage = () => <AdminSectionPage config={sectionConfigs.actionLog} />;
export const AdminReportsPage = () => <AdminSectionPage config={sectionConfigs.reports} />;

export const AdminAnalyticsPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Аналитика"
      subtitle="Сводка активности, прогресса потоков и качества заданий."
      actions={<Button>Обновить</Button>}
    />

    <div className="metric-grid">
      <Card>
        <div className="metric-card">
          <span className="muted">Решений за сутки</span>
          <strong>312</strong>
          <Badge tone="success">+18%</Badge>
        </div>
      </Card>
      <Card>
        <div className="metric-card">
          <span className="muted">Средний проход теста</span>
          <strong>72%</strong>
          <Badge tone="info">стабильно</Badge>
        </div>
      </Card>
      <Card>
        <div className="metric-card">
          <span className="muted">Сложные задания</span>
          <strong>6</strong>
          <Badge tone="danger">ниже 15%</Badge>
        </div>
      </Card>
    </div>

    <div className="grid grid-2">
      <Card>
        <div className="page-stack">
          <h3>Активность по дням</h3>
          <div className="bar-chart" aria-label="Активность по дням">
            {[44, 62, 58, 80, 76, 92, 68].map((height, index) => (
              <span key={index} style={{ height: `${height}%` }} />
            ))}
          </div>
        </div>
      </Card>

      <Card>
        <div className="page-stack">
          <h3>Сигналы для администратора</h3>
          <ul className="page-list">
            <li>Проверить задания с большим числом неверных попыток</li>
            <li>Сверить лабораторные баллы перед публикацией рейтинга</li>
            <li>Назначить проверяющего для очереди write-up</li>
          </ul>
        </div>
      </Card>
    </div>
  </div>
);
