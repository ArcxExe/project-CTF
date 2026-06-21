import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { adminChallengesApi } from "@/shared/api/services/challenges";
import type { Challenge } from "@/shared/api/services/challenges";
import { adminCompetitionsApi } from "@/shared/api/services/competitions";
import { adminPromoCodesApi } from "@/shared/api/services/promoCodes";
import type { PromoCode } from "@/shared/api/services/promoCodes";
import { adminTestsApi } from "@/shared/api/services/tests";
import type { CtfTest, QuizQuestion, QuizOption } from "@/shared/api/services/tests";
import type { Competition } from "@/shared/types/competition";
import { adminAttemptsApi } from "@/shared/api/services/attempts";
import type { Attempt } from "@/shared/api/services/attempts";
import { studentsApi } from "@/shared/api/services/students";
import type { Student } from "@/shared/types/education";
import { scoreAdjustmentsApi } from "@/shared/api/services/scoreAdjustments";
import { leaderboardApi } from "@/shared/api/services/leaderboard";
import type { LeaderboardRow } from "@/shared/api/services/leaderboard";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";
import { labScoresApi } from "@/shared/api/services/labScores";
import { auditLogsApi } from "@/shared/api/services/auditLogs";
import { reportsApi } from "@/shared/api/services/reports";
import { gradingScalesApi, GradingScale, GradingScalePayload } from "@/shared/api/services/gradingScales";

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


export const testStatusLabels: Record<CtfTest["status"], string> = {
  draft: "Черновик",
  published: "Опубликовано",
  archived: "Архив",
};

const formatDateTime = (value?: string) => (value ? new Date(value).toLocaleString("ru-RU") : "—");

const AdminTasksManagerPage = () => {
  const { push } = useToastStore();
  const [tasks, setTasks] = useState<Challenge[]>([]);
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState({
    competitionId: "",
    title: "",
    description: "",
    points: "100",
    flag: "",
  });

  useEffect(() => {
    void Promise.all([adminChallengesApi.getAll(), adminCompetitionsApi.getAll()])
      .then(([challengeRows, competitionRows]) => {
        setTasks(challengeRows);
        setCompetitions(competitionRows);
        setForm((current) => ({
          ...current,
          competitionId: current.competitionId || competitionRows[0]?.id || "",
        }));
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить задания",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const filteredTasks = useMemo(
    () =>
      tasks.filter((task) =>
        [task.title, task.description]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase()),
      ),
    [search, tasks],
  );

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      const created = await adminChallengesApi.create({
        competitionId: form.competitionId,
        title: form.title,
        description: form.description || undefined,
        points: Number(form.points),
        flag: form.flag,
      });

      setTasks((current) => [created, ...current]);
      setForm({
        competitionId: competitions[0]?.id || "",
        title: "",
        description: "",
        points: "100",
        flag: "",
      });
      setIsModalOpen(false);
      push({ title: "Задание создано", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось создать задание",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handlePublish = async (_task: Challenge) => {
    push({
      title: "Публикация через API пока не поддерживается",
      variant: "info",
    });
  };

  const handleDelete = async (task: Challenge) => {
    if (!window.confirm(`Удалить задание "${task.title}"?`)) {
      return;
    }

    try {
      await adminChallengesApi.delete(task.id);
      setTasks((current) => current.filter((item) => item.id !== task.id));
      push({ title: "Задание удалено", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить задание",
        variant: "error",
      });
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем задания..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Задания"
        subtitle="Банк CTF-заданий из backend API: создание, публикация и удаление."
        actions={<Button onClick={() => setIsModalOpen(true)}>Создать задание</Button>}
      />

      <div className="metric-grid">
        <Card>
          <div className="metric-card">
            <span className="muted">Всего</span>
            <strong>{tasks.length}</strong>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Опубликовано</span>
            <strong>{tasks.length}</strong>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Баллов всего</span>
            <strong>{tasks.reduce((sum, task) => sum + task.points, 0)}</strong>
          </div>
        </Card>
      </div>

      <Card>
        <Input
          label="Поиск"
          placeholder="Название, категория, описание"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </Card>

      <DataTable
        columns={[
          { key: "title", title: "Задание" },
          { key: "points", title: "Баллы" },
          {
            key: "competitionId",
            title: "Соревнование",
            render: (task) => {
              const comp = competitions.find((c) => c.id === task.competitionId);
              return comp ? comp.title : task.competitionId;
            },
          },
          {
            key: "actions",
            title: "Действия",
            render: (task) => (
              <div className="table-actions">
                {task.status === "draft" && (
                  <Button variant="secondary" onClick={() => void handlePublish(task)}>
                    Опубликовать
                  </Button>
                )}
                <Button variant="danger" onClick={() => void handleDelete(task)}>
                  Удалить
                </Button>
              </div>
            ),
          },
        ]}
        rows={filteredTasks}
      />

      <Modal open={isModalOpen} title="Новое задание" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleCreate}>
          <label className="ui-field">
            <span className="ui-field__label">Соревнование</span>
            <select
              className="ui-input"
              value={form.competitionId}
              onChange={(event) => setForm((current) => ({ ...current, competitionId: event.target.value }))}
              required
            >
              {competitions.map((competition) => (
                <option key={competition.id} value={competition.id}>
                  {competition.title}
                </option>
              ))}
            </select>
          </label>

          <Input
            label="Название"
            value={form.title}
            onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            required
          />

          <label className="ui-field">
            <span className="ui-field__label">Описание</span>
            <textarea
              className="ui-input ui-textarea"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <div className="admin-form-grid">
            <Input
              label="Баллы"
              type="number"
              min={1}
              value={form.points}
              onChange={(event) => setForm((current) => ({ ...current, points: event.target.value }))}
              required
            />
          </div>

          <Input
            label="Флаг"
            placeholder="CTF{example}"
            value={form.flag}
            onChange={(event) => setForm((current) => ({ ...current, flag: event.target.value }))}
            required
          />

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохраняем..." : "Создать"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};

const AdminTestsManagerPage = () => {
  const { push } = useToastStore();
  const [tests, setTests] = useState<CtfTest[]>([]);
  const [challenges, setChallenges] = useState<Challenge[]>([]);
  const [testChallenges, setTestChallenges] = useState<Challenge[]>([]);
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [selectedTestId, setSelectedTestId] = useState("");
  const [selectedChallengeId, setSelectedChallengeId] = useState("");
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTestId, setEditingTestId] = useState<string | null>(null);

  // Quiz Question Constructor states
  const [isConstructorOpen, setIsConstructorOpen] = useState(false);
  const [constructorTest, setConstructorTest] = useState<CtfTest | null>(null);
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [selectedQuestionId, setSelectedQuestionId] = useState<string | null>(null);
  const [isQuestionsLoading, setIsQuestionsLoading] = useState(false);

  const [questionForm, setQuestionForm] = useState({
    text: "",
    type: "RADIO" as QuizQuestion["type"],
    points: "10",
  });
  const [editingQuestionId, setEditingQuestionId] = useState<string | null>(null);

  const [optionForm, setOptionForm] = useState({
    text: "",
    isCorrect: false,
    sequenceOrder: "1",
  });

  const [form, setForm] = useState({
    title: "",
    description: "",
    status: "published" as CtfTest["status"],
    timeLimitMinutes: "30",
    passingScore: "70",
    questionsCount: "10",
    competitionId: "",
  });

  const loadData = async () => {
    try {
      const [testRows, challengeRows, competitionRows] = await Promise.all([
        adminTestsApi.getAll(),
        adminChallengesApi.getAll(),
        adminCompetitionsApi.getAll()
      ]);
      setTests(testRows);
      setChallenges(challengeRows);
      setCompetitions(competitionRows);
      setSelectedTestId(testRows[0]?.id ?? "");
      setSelectedChallengeId(challengeRows[0]?.id ?? "");
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить данные",
        variant: "error",
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [push]);

  useEffect(() => {
    if (!selectedTestId) {
      setTestChallenges([]);
      return;
    }

    void adminTestsApi
      .getChallenges(selectedTestId)
      .then(setTestChallenges)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить задания теста",
          variant: "error",
        });
      });
  }, [push, selectedTestId]);

  const filteredTests = useMemo(
    () =>
      tests.filter((test) =>
        [test.title, test.description, test.status].join(" ").toLowerCase().includes(search.toLowerCase()),
      ),
    [search, tests],
  );

  const handleCreateClick = () => {
    setEditingTestId(null);
    setForm({
      title: "",
      description: "",
      status: "published",
      timeLimitMinutes: "30",
      passingScore: "70",
      questionsCount: "10",
      competitionId: "",
    });
    setIsModalOpen(true);
  };

  const handleEditTest = (test: CtfTest) => {
    setEditingTestId(test.id);
    setForm({
      title: test.title,
      description: test.description || "",
      status: test.status,
      timeLimitMinutes: String(test.timeLimitMinutes),
      passingScore: String(test.passingScore),
      questionsCount: String(test.questionsCount),
      competitionId: test.competitionId || "",
    });
    setIsModalOpen(true);
  };

  const handleDeleteTest = async (id: string) => {
    if (!window.confirm("Вы действительно хотите удалить этот тест?")) {
      return;
    }

    try {
      await adminTestsApi.delete(id);
      setTests((current) => current.filter((t) => t.id !== id));
      if (selectedTestId === id) {
        setSelectedTestId("");
      }
      push({ title: "Тест удален", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить тест",
        variant: "error",
      });
    }
  };

  const handleStatusChange = async (test: CtfTest, newStatus: CtfTest["status"]) => {
    try {
      const updated = await adminTestsApi.update(test.id, {
        title: test.title,
        description: test.description || undefined,
        status: newStatus,
        timeLimitMinutes: test.timeLimitMinutes,
        passingScore: test.passingScore,
        questionsCount: test.questionsCount,
        competitionId: test.competitionId || undefined,
      });
      setTests((current) => current.map((t) => (t.id === test.id ? updated : t)));
      push({ title: "Статус теста обновлен", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось обновить статус",
        variant: "error",
      });
    }
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    const payload = {
      title: form.title,
      description: form.description || undefined,
      status: form.status,
      timeLimitMinutes: Number(form.timeLimitMinutes),
      passingScore: Number(form.passingScore),
      questionsCount: Number(form.questionsCount),
      competitionId: form.competitionId || undefined,
    };

    try {
      if (editingTestId) {
        const updated = await adminTestsApi.update(editingTestId, payload);
        setTests((current) => current.map((t) => (t.id === editingTestId ? updated : t)));
        push({ title: "Тест обновлен", variant: "success" });
      } else {
        const created = await adminTestsApi.create(payload);
        setTests((current) => [created, ...current]);
        setSelectedTestId(created.id);
        push({ title: "Тест создан", variant: "success" });
      }
      setForm({
        title: "",
        description: "",
        status: "published",
        timeLimitMinutes: "30",
        passingScore: "70",
        questionsCount: "10",
        competitionId: "",
      });
      setIsModalOpen(false);
      setEditingTestId(null);
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить тест",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddChallenge = async () => {
    if (!selectedTestId || !selectedChallengeId) {
      return;
    }

    try {
      const updated = await adminTestsApi.addChallenge(selectedTestId, selectedChallengeId);
      setTests((current) => current.map((test) => (test.id === updated.id ? updated : test)));
      setTestChallenges(await adminTestsApi.getChallenges(selectedTestId));
      push({ title: "Задание добавлено в тест", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось добавить задание",
        variant: "error",
      });
    }
  };

  const handleRemoveChallenge = async (challengeId: string) => {
    if (!selectedTestId) {
      return;
    }

    try {
      const updated = await adminTestsApi.removeChallenge(selectedTestId, challengeId);
      setTests((current) => current.map((test) => (test.id === updated.id ? updated : test)));
      setTestChallenges((current) => current.filter((challenge) => challenge.id !== challengeId));
      push({ title: "Задание удалено из теста", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить задание",
        variant: "error",
      });
    }
  };

  // Quiz Question Constructor handlers
  const handleOpenQuestionsConstructor = (test: CtfTest) => {
    setConstructorTest(test);
    setQuestions([]);
    setSelectedQuestionId(null);
    setIsQuestionsLoading(true);
    setIsConstructorOpen(true);

    void adminTestsApi.getAdminQuestions(test.id)
      .then((data) => {
        setQuestions(data);
        if (data.length > 0) {
          setSelectedQuestionId(data[0].id);
        }
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить вопросы теста",
          variant: "error",
        });
      })
      .finally(() => {
        setIsQuestionsLoading(false);
      });
  };

  const handleStartEditQuestion = (q: QuizQuestion) => {
    setEditingQuestionId(q.id);
    setQuestionForm({
      text: q.text,
      type: q.type,
      points: String(q.points),
    });
  };

  const handleCancelEditQuestion = () => {
    setEditingQuestionId(null);
    setQuestionForm({
      text: "",
      type: "RADIO",
      points: "10",
    });
  };

  const handleSaveQuestion = async (event: FormEvent) => {
    event.preventDefault();
    if (!constructorTest) return;
    setIsSaving(true);

    try {
      if (editingQuestionId) {
        const updated = await adminTestsApi.updateQuestion(editingQuestionId, {
          text: questionForm.text,
          type: questionForm.type,
          points: Number(questionForm.points),
          ordering: questions.find(q => q.id === editingQuestionId)?.ordering || 1,
        });
        setQuestions(current => current.map(q => q.id === editingQuestionId ? { ...q, ...updated, options: q.options } : q));
        push({ title: "Вопрос обновлен", variant: "success" });
      } else {
        const nextOrdering = questions.reduce((max, q) => Math.max(max, q.ordering), 0) + 1;
        const created = await adminTestsApi.addQuestion(constructorTest.id, {
          text: questionForm.text,
          type: questionForm.type,
          points: Number(questionForm.points),
          ordering: nextOrdering,
        });
        setQuestions(current => [...current, created]);
        setSelectedQuestionId(created.id);
        push({ title: "Вопрос добавлен", variant: "success" });
      }
      handleCancelEditQuestion();
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить вопрос",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteQuestion = async (id: string) => {
    if (!window.confirm("Вы действительно хотите удалить этот вопрос?")) {
      return;
    }
    try {
      await adminTestsApi.deleteQuestion(id);
      setQuestions(current => current.filter(q => q.id !== id));
      if (selectedQuestionId === id) {
        setSelectedQuestionId(null);
      }
      push({ title: "Вопрос удален", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить вопрос",
        variant: "error",
      });
    }
  };

  const handleAddOption = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedQuestionId) return;
    setIsSaving(true);

    try {
      const created = await adminTestsApi.addOption(selectedQuestionId, {
        optionText: optionForm.text,
        isCorrect: optionForm.isCorrect,
        sequenceOrder: optionForm.sequenceOrder ? Number(optionForm.sequenceOrder) : undefined,
      });
      setQuestions(current => current.map(q => {
        if (q.id === selectedQuestionId) {
          return { ...q, options: [...q.options, created] };
        }
        return q;
      }));
      setOptionForm({
        text: "",
        isCorrect: false,
        sequenceOrder: "1",
      });
      push({ title: "Вариант ответа добавлен", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось добавить вариант ответа",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteOption = async (optionId: string) => {
    if (!window.confirm("Вы действительно хотите удалить этот вариант ответа?")) {
      return;
    }
    try {
      await adminTestsApi.deleteOption(optionId);
      setQuestions(current => current.map(q => {
        if (q.id === selectedQuestionId) {
          return { ...q, options: q.options.filter(o => o.id !== optionId) };
        }
        return q;
      }));
      push({ title: "Вариант ответа удален", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить вариант ответа",
        variant: "error",
      });
    }
  };

  const handleToggleOptionCorrect = async (option: QuizOption, isCorrect: boolean) => {
    try {
      const updated = await adminTestsApi.updateOption(option.id, {
        isCorrect,
      });
      setQuestions(current => current.map(q => {
        if (q.id === selectedQuestionId) {
          return {
            ...q,
            options: q.options.map(o => o.id === option.id ? { ...o, isCorrect: updated.isCorrect } : o)
          };
        }
        return q;
      }));
      push({ title: "Ответ обновлен", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось обновить вариант ответа",
        variant: "error",
      });
    }
  };

  const selectedQuestion = questions.find(q => q.id === selectedQuestionId);

  if (isLoading) {
    return <Loader label="Загружаем тесты..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Тесты"
        subtitle="Предварительные тесты допуска из backend API."
        actions={<Button onClick={handleCreateClick}>Создать тест</Button>}
      />

      <Card>
        <Input
          label="Поиск"
          placeholder="Название, описание, статус"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </Card>

      <DataTable
        columns={[
          { key: "title", title: "Название" },
          { key: "questionsCount", title: "Вопросов" },
          { key: "timeLimitMinutes", title: "Лимит, мин" },
          { key: "passingScore", title: "Проходной %" },
          {
            key: "status",
            title: "Статус",
            render: (test) => (
              <select
                className="ui-input"
                style={{ padding: "0.25rem 0.5rem", height: "auto", fontSize: "0.85rem", width: "auto", minWidth: "120px" }}
                value={test.status}
                onChange={(e) => void handleStatusChange(test, e.target.value as CtfTest["status"])}
              >
                <option value="draft">Черновик</option>
                <option value="published">Опубликовано</option>
                <option value="archived">Архив</option>
              </select>
            ),
          },
          {
            key: "actions",
            title: "Действия",
            render: (test) => (
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <Button variant="secondary" onClick={() => handleEditTest(test)}>
                  Редактировать
                </Button>
                <Button onClick={() => handleOpenQuestionsConstructor(test)}>
                  Вопросы
                </Button>
                <Button variant="danger" onClick={() => void handleDeleteTest(test.id)}>
                  Удалить
                </Button>
              </div>
            ),
          },
        ]}
        rows={filteredTests}
      />

      <Card>
        <div className="page-stack">
          <div className="admin-form-grid">
            <label className="ui-field">
              <span className="ui-field__label">Тест</span>
              <select
                className="ui-input"
                value={selectedTestId}
                onChange={(event) => setSelectedTestId(event.target.value)}
              >
                {tests.map((test) => (
                  <option key={test.id} value={test.id}>
                    {test.title}
                  </option>
                ))}
              </select>
            </label>
            <label className="ui-field">
              <span className="ui-field__label">Задание</span>
              <select
                className="ui-input"
                value={selectedChallengeId}
                onChange={(event) => setSelectedChallengeId(event.target.value)}
              >
                {challenges.map((challenge) => (
                  <option key={challenge.id} value={challenge.id}>
                    {challenge.title} · {challenge.points} pts
                  </option>
                ))}
              </select>
            </label>
            <Button type="button" onClick={() => void handleAddChallenge()}>
              Добавить задание
            </Button>
          </div>

          <DataTable
            columns={[
              { key: "title", title: "Задание" },
              { key: "points", title: "Баллы" },
              {
                key: "competitionId",
                title: "Соревнование",
                render: (challenge) => {
                  const comp = competitions.find((c) => c.id === challenge.competitionId);
                  return comp ? comp.title : challenge.competitionId;
                },
              },
              {
                key: "actions",
                title: "Действия",
                render: (challenge) => (
                  <Button variant="danger" onClick={() => void handleRemoveChallenge(challenge.id)}>
                    Удалить из теста
                  </Button>
                ),
              },
            ]}
            rows={testChallenges}
          />
        </div>
      </Card>

      <Modal open={isModalOpen} title={editingTestId ? "Редактировать тест" : "Новый тест"} onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleSubmit}>
          <Input
            label="Название"
            value={form.title}
            onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            required
          />

          <label className="ui-field">
            <span className="ui-field__label">Описание</span>
            <textarea
              className="ui-input ui-textarea"
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            />
          </label>

          <label className="ui-field">
            <span className="ui-field__label">Соревнование</span>
            <select
              className="ui-input"
              value={form.competitionId}
              onChange={(event) => setForm((current) => ({ ...current, competitionId: event.target.value }))}
            >
              <option value="">Не связано</option>
              {competitions.map((comp) => (
                <option key={comp.id} value={comp.id}>
                  {comp.title}
                </option>
              ))}
            </select>
          </label>

          <div className="admin-form-grid">
            <Input
              label="Лимит, мин"
              type="number"
              min={1}
              max={480}
              value={form.timeLimitMinutes}
              onChange={(event) => setForm((current) => ({ ...current, timeLimitMinutes: event.target.value }))}
              required
            />
            <Input
              label="Проходной %"
              type="number"
              min={1}
              max={100}
              value={form.passingScore}
              onChange={(event) => setForm((current) => ({ ...current, passingScore: event.target.value }))}
              required
            />
            <Input
              label="Вопросов"
              type="number"
              min={0}
              value={form.questionsCount}
              onChange={(event) => setForm((current) => ({ ...current, questionsCount: event.target.value }))}
              required
            />
            <label className="ui-field">
              <span className="ui-field__label">Статус</span>
              <select
                className="ui-input"
                value={form.status}
                onChange={(event) =>
                  setForm((current) => ({ ...current, status: event.target.value as CtfTest["status"] }))
                }
              >
                <option value="published">Опубликовано</option>
                <option value="draft">Черновик</option>
                <option value="archived">Архив</option>
              </select>
            </label>
          </div>

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохраняем..." : (editingTestId ? "Сохранить" : "Создать")}
          </Button>
        </form>
      </Modal>

      {/* Quiz Question Constructor Modal */}
      <Modal
        open={isConstructorOpen}
        title={`Конструктор вопросов: ${constructorTest?.title || ""}`}
        onClose={() => {
          setIsConstructorOpen(false);
          setConstructorTest(null);
          setQuestions([]);
          setSelectedQuestionId(null);
        }}
      >
        <div className="constructor-layout" style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "1.5rem", minHeight: "480px", maxHeight: "650px", overflow: "hidden" }}>
          {/* Left Column: Questions List & Add/Edit Question */}
          <div style={{ display: "flex", flexDirection: "column", borderRight: "1px solid var(--border)", paddingRight: "1.5rem", overflowY: "auto" }}>
            <h4>Вопросы ({questions.length})</h4>
            {isQuestionsLoading ? (
              <Loader label="Загрузка вопросов..." />
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", marginTop: "0.5rem" }}>
                {questions.map((q) => (
                  <div
                    key={q.id}
                    onClick={() => setSelectedQuestionId(q.id)}
                    style={{
                      padding: "0.75rem",
                      borderRadius: "var(--radius)",
                      border: q.id === selectedQuestionId ? "2px solid var(--primary)" : "1px solid var(--border)",
                      background: q.id === selectedQuestionId ? "rgba(var(--primary-rgb, 79, 70, 229), 0.08)" : "var(--card-bg)",
                      cursor: "pointer",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "flex-start",
                      gap: "0.5rem"
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--primary)", fontWeight: "bold" }}>
                        {q.type} · {q.points} баллов · Порядок: {q.ordering}
                      </div>
                      <div style={{ fontWeight: "500", marginTop: "0.25rem" }}>{q.text}</div>
                    </div>
                    <div style={{ display: "flex", gap: "0.5rem" }}>
                      <button
                        type="button"
                        style={{ background: "none", border: "none", cursor: "pointer", fontSize: "0.95rem" }}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleStartEditQuestion(q);
                        }}
                        title="Редактировать вопрос"
                      >
                        ✏️
                      </button>
                      <button
                        type="button"
                        style={{ background: "none", border: "none", cursor: "pointer", fontSize: "0.95rem" }}
                        onClick={(e) => {
                          e.stopPropagation();
                          void handleDeleteQuestion(q.id);
                        }}
                        title="Удалить вопрос"
                      >
                        ❌
                      </button>
                    </div>
                  </div>
                ))}
                {questions.length === 0 && <div className="muted" style={{ padding: "1rem 0" }}>Вопросов пока нет. Добавьте первый вопрос ниже.</div>}
              </div>
            )}

            {/* Add/Edit Question Form */}
            <form onSubmit={handleSaveQuestion} style={{ marginTop: "auto", paddingTop: "1.5rem", borderTop: "1px dashed var(--border)" }}>
              <h5>{editingQuestionId ? "Редактировать вопрос" : "Добавить вопрос"}</h5>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", marginTop: "0.5rem" }}>
                <label className="ui-field">
                  <span className="ui-field__label" style={{ fontSize: "0.8rem" }}>Текст вопроса</span>
                  <input
                    type="text"
                    className="ui-input"
                    value={questionForm.text}
                    onChange={(e) => setQuestionForm(c => ({ ...c, text: e.target.value }))}
                    required
                  />
                </label>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.5rem" }}>
                  <label className="ui-field">
                    <span className="ui-field__label" style={{ fontSize: "0.8rem" }}>Тип</span>
                    <select
                      className="ui-input"
                      value={questionForm.type}
                      onChange={(e) => setQuestionForm(c => ({ ...c, type: e.target.value as any }))}
                    >
                      <option value="RADIO">Один выбор (Radio)</option>
                      <option value="CHECKBOX">Множ. выбор (Checkbox)</option>
                      <option value="SEQUENCE">Последовательность</option>
                    </select>
                  </label>
                  <label className="ui-field">
                    <span className="ui-field__label" style={{ fontSize: "0.8rem" }}>Баллы</span>
                    <input
                      type="number"
                      className="ui-input"
                      min={0}
                      value={questionForm.points}
                      onChange={(e) => setQuestionForm(c => ({ ...c, points: e.target.value }))}
                      required
                    />
                  </label>
                </div>
                <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.25rem" }}>
                  <Button type="submit" disabled={isSaving}>
                    {editingQuestionId ? "Сохранить" : "Добавить"}
                  </Button>
                  {editingQuestionId && (
                    <Button type="button" variant="secondary" onClick={handleCancelEditQuestion}>
                      Отмена
                    </Button>
                  )}
                </div>
              </div>
            </form>
          </div>

          {/* Right Column: Selected Question Options */}
          <div style={{ display: "flex", flexDirection: "column", overflowY: "auto" }}>
            {selectedQuestion ? (
              <>
                <h4>Варианты ответов</h4>
                <div style={{ fontSize: "0.85rem", color: "var(--muted-color, #666)", marginBottom: "0.5rem" }}>
                  Вопрос: <strong>{selectedQuestion.text}</strong>
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", marginTop: "0.5rem" }}>
                  {selectedQuestion.options.map((o) => (
                    <div
                      key={o.id}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        padding: "0.5rem 0.75rem",
                        borderRadius: "var(--radius)",
                        border: "1px solid var(--border)",
                        background: "var(--card-bg)",
                        gap: "0.5rem"
                      }}
                    >
                      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", flex: 1 }}>
                        {selectedQuestion.type !== "SEQUENCE" ? (
                          <input
                            type="checkbox"
                            checked={o.isCorrect}
                            onChange={(e) => void handleToggleOptionCorrect(o, e.target.checked)}
                            title="Правильный ответ"
                          />
                        ) : (
                          <span style={{
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            width: "20px",
                            height: "20px",
                            borderRadius: "50%",
                            background: "var(--primary)",
                            color: "white",
                            fontSize: "0.75rem",
                            fontWeight: "bold"
                          }}>
                            {o.sequenceOrder ?? 0}
                          </span>
                        )}
                        <span style={{ fontSize: "0.95rem" }}>{o.text}</span>
                      </div>
                      <button
                        type="button"
                        style={{ background: "none", border: "none", cursor: "pointer", fontSize: "0.95rem" }}
                        onClick={() => void handleDeleteOption(o.id)}
                        title="Удалить вариант ответа"
                      >
                        ❌
                      </button>
                    </div>
                  ))}
                  {selectedQuestion.options.length === 0 && (
                    <div className="muted" style={{ padding: "1rem 0" }}>Вариантов ответов нет. Создайте первый вариант ниже.</div>
                  )}
                </div>

                {/* Add Option Form */}
                <form onSubmit={handleAddOption} style={{ marginTop: "auto", paddingTop: "1.5rem", borderTop: "1px dashed var(--border)" }}>
                  <h5>Добавить вариант ответа</h5>
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", marginTop: "0.5rem" }}>
                    <label className="ui-field">
                      <span className="ui-field__label" style={{ fontSize: "0.8rem" }}>Текст варианта</span>
                      <input
                        type="text"
                        className="ui-input"
                        value={optionForm.text}
                        onChange={(e) => setOptionForm(c => ({ ...c, text: e.target.value }))}
                        required
                      />
                    </label>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                      {selectedQuestion.type !== "SEQUENCE" ? (
                        <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
                          <input
                            type="checkbox"
                            checked={optionForm.isCorrect}
                            onChange={(e) => setOptionForm(c => ({ ...c, isCorrect: e.target.checked }))}
                          />
                          <span style={{ fontSize: "0.85rem" }}>Правильный ответ</span>
                        </label>
                      ) : (
                        <label className="ui-field" style={{ width: "120px" }}>
                          <span className="ui-field__label" style={{ fontSize: "0.8rem" }}>Порядок (1, 2, ...)</span>
                          <input
                            type="number"
                            className="ui-input"
                            min={0}
                            value={optionForm.sequenceOrder}
                            onChange={(e) => setOptionForm(c => ({ ...c, sequenceOrder: e.target.value }))}
                            required
                        />
                        </label>
                      )}
                      <Button type="submit" disabled={isSaving}>
                        Добавить
                      </Button>
                    </div>
                  </div>
                </form>
              </>
            ) : (
              <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center", color: "var(--muted-color)", minHeight: "200px" }}>
                Выберите вопрос слева, чтобы просмотреть варианты ответов.
              </div>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
};


const AdminPromoCodesManagerPage = () => {
  const { push } = useToastStore();
  const [promoCodes, setPromoCodes] = useState<PromoCode[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState({
    code: "",
    modifierType: "FIXED_ADD" as PromoCode["modifierType"],
    value: "50",
    maxUses: "1",
  });

  useEffect(() => {
    void adminPromoCodesApi
      .getAll()
      .then(setPromoCodes)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить промокоды",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const filteredPromoCodes = useMemo(
    () =>
      promoCodes.filter((promoCode) =>
        [promoCode.code, promoCode.modifierType, promoCode.usedByStudentName || ""]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase()),
      ),
    [promoCodes, search],
  );

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      const created = await adminPromoCodesApi.create({
        code: form.code,
        modifierType: form.modifierType,
        value: Number(form.value) || 0,
        maxUses: Number(form.maxUses) || 1,
      });

      setPromoCodes((current) => [created, ...current]);
      setForm({
        code: "",
        modifierType: "FIXED_ADD",
        value: "50",
        maxUses: "1",
      });
      setIsModalOpen(false);
      push({ title: "Промокод создан", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось создать промокод",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Вы уверены, что хотите удалить этот промокод?")) {
      return;
    }
    setIsSaving(true);
    try {
      await adminPromoCodesApi.delete(id);
      setPromoCodes((current) => current.filter((item) => item.id !== id));
      push({ title: "Промокод удален", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить промокод",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем промокоды..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Промокоды"
        subtitle="Промокоды для бонусов, подсказок и специальных условий соревнования."
        actions={<Button onClick={() => setIsModalOpen(true)}>Создать промокод</Button>}
      />

      <Card>
        <Input
          label="Поиск"
          placeholder="Код, тип, имя студента"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </Card>

      <DataTable
        columns={[
          { key: "code", title: "Код" },
          {
            key: "modifierType",
            title: "Тип бонуса",
            render: (promoCode) => {
              switch (promoCode.modifierType) {
                case "FIXED_ADD":
                  return "Добавить баллы";
                case "FIXED_SUB":
                  return "Вычесть баллы";
                case "DOUBLE_COEFF":
                  return "Умножить (x2)";
                default:
                  return promoCode.modifierType;
              }
            },
          },
          { key: "value", title: "Значение" },
          {
            key: "maxUses",
            title: "Использовано",
            render: (promoCode) => (
              <span>
                {promoCode.usedCount} из {promoCode.maxUses}
              </span>
            ),
          },
          {
            key: "usedByStudentName",
            title: "Кем использован",
            render: (promoCode) => {
              if (promoCode.usedCount > 0) {
                return (
                  <span>
                    👤 {promoCode.usedByStudentName || "Студент"}{" "}
                    {promoCode.usedAt ? `(${formatDateTime(promoCode.usedAt)})` : ""}
                  </span>
                );
              }
              return <span className="muted">Не использован</span>;
            },
          },
          {
            key: "actions",
            title: "Действия",
            render: (promoCode) => (
              <Button
                variant="danger"
                onClick={() => handleDelete(promoCode.id)}
                disabled={isSaving}
              >
                Удалить
              </Button>
            ),
          },
        ]}
        rows={filteredPromoCodes}
      />

      <Modal open={isModalOpen} title="Новый промокод" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleCreate}>
          <div className="admin-form-grid">
            <Input
              label="Код"
              value={form.code}
              onChange={(event) => setForm((current) => ({ ...current, code: event.target.value.toUpperCase() }))}
              required
            />
            <label className="ui-field">
              <span className="ui-field__label">Тип бонуса</span>
              <select
                className="ui-input"
                value={form.modifierType}
                onChange={(event) =>
                  setForm((current) => ({ ...current, modifierType: event.target.value as PromoCode["modifierType"] }))
                }
                required
              >
                <option value="FIXED_ADD">Добавить баллы</option>
                <option value="FIXED_SUB">Вычесть баллы</option>
                <option value="DOUBLE_COEFF">Умножить (x2)</option>
              </select>
            </label>
            <Input
              label="Значение"
              type="number"
              value={form.value}
              onChange={(event) => setForm((current) => ({ ...current, value: event.target.value }))}
              required
            />
            <Input
              label="Лимит использований"
              type="number"
              value={form.maxUses}
              onChange={(event) => setForm((current) => ({ ...current, maxUses: event.target.value }))}
              required
            />
          </div>

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохраняем..." : "Создать"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};


export const AdminGroupsPage = () => {
  const { push } = useToastStore();
  const [groups, setGroups] = useState<any[]>([]);
  const [streams, setStreams] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState({ name: "", streamId: "" });

  useEffect(() => {
    Promise.all([
      groupsApi.getAll(),
      streamsApi.getAll().catch(() => [])
    ])
    .then(([groupsData, streamsData]) => {
      setGroups(groupsData);
      setStreams(streamsData);
    })
    .catch((error) => {
      push({ title: error instanceof Error ? error.message : "Ошибка загрузки", variant: "error" });
    })
    .finally(() => setIsLoading(false));
  }, [push]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {

      const created = await groupsApi.create({
        name: form.name,
        streamId: form.streamId || undefined,
      });
      setGroups((curr) => [created, ...curr]);
      setIsModalOpen(false);
      setForm({ name: "", streamId: "" });
      push({ title: "Группа создана", variant: "success" });
    } catch (error) {
      push({ title: error instanceof Error ? error.message : "Ошибка", variant: "error" });
    } finally {
      setIsSaving(false);
    }
  };

  const filtered = groups.filter((g) => g.name.toLowerCase().includes(search.toLowerCase()));

  if (isLoading) return <Loader label="Загрузка групп..." />;

  return (
    <div className="page-stack">
      <PageHeader title="Группы" subtitle="Учебные группы, привязка к потокам" actions={<Button onClick={() => setIsModalOpen(true)}>Добавить группу</Button>} />
      <Card><Input label="Поиск" value={search} onChange={(e) => setSearch(e.target.value)} /></Card>
      <DataTable
        columns={[
          { key: "name", title: "Группа" },
          { key: "streamId", title: "Поток", render: (g) => streams.find(s => s.id === g.streamId)?.name || "Не назначен" },
          { key: "actions", title: "Действия", render: (g) => <Button variant="danger" onClick={async () => {
            if(!confirm("Удалить?")) return;

            await groupsApi.delete(g.id);
            setGroups(groups.filter(gr => gr.id !== g.id));
          }}>Удалить</Button> }
        ]}
        rows={filtered}
      />
      <Modal open={isModalOpen} title="Новая группа" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleCreate}>
          <Input label="Название" value={form.name} onChange={(e) => setForm(c => ({...c, name: e.target.value}))} required />
          <label className="ui-field">
            <span className="ui-field__label">Поток</span>
            <select className="ui-input" value={form.streamId} onChange={(e) => setForm(c => ({...c, streamId: e.target.value}))}>
              <option value="">Не назначен</option>
              {streams.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </label>
          <Button type="submit" disabled={isSaving}>{isSaving ? "Сохранение..." : "Добавить"}</Button>
        </form>
      </Modal>
    </div>
  );
};


export const AdminStreamsPage = () => {
  const { push } = useToastStore();
  const [streams, setStreams] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState({ name: "" });

  useEffect(() => {
    streamsApi.getAll()
      .then(setStreams)
      .catch(() => push({ title: "Ошибка загрузки", variant: "error" }))
      .finally(() => setIsLoading(false));
  }, [push]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {

      const created = await streamsApi.create(form);
      setStreams(curr => [created, ...curr]);
      setIsModalOpen(false);
      setForm({ name: "" });
      push({ title: "Поток создан", variant: "success" });
    } catch (error) {
      push({ title: "Ошибка", variant: "error" });
    } finally {
      setIsSaving(false);
    }
  };

  const filtered = streams.filter(s => s.name.toLowerCase().includes(search.toLowerCase()));

  if (isLoading) return <Loader label="Загрузка потоков..." />;

  return (
    <div className="page-stack">
      <PageHeader title="Потоки" subtitle="Потоки обучения" actions={<Button onClick={() => setIsModalOpen(true)}>Создать поток</Button>} />
      <Card><Input label="Поиск" value={search} onChange={(e) => setSearch(e.target.value)} /></Card>
      <DataTable
        columns={[
          { key: "name", title: "Поток" },
          { key: "groupsCount", title: "Групп" },
          { key: "studentsCount", title: "Студентов" },
          { key: "actions", title: "Действия", render: (s) => <Button variant="danger" onClick={async () => {
            if(!confirm("Удалить?")) return;

            await streamsApi.delete(s.id);
            setStreams(streams.filter(st => st.id !== s.id));
          }}>Удалить</Button> }
        ]}
        rows={filtered}
      />
      <Modal open={isModalOpen} title="Новый поток" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleCreate}>
          <Input label="Название" value={form.name} onChange={(e) => setForm({name: e.target.value})} required />
          <Button type="submit" disabled={isSaving}>{isSaving ? "Сохранение..." : "Добавить"}</Button>
        </form>
      </Modal>
    </div>
  );
};


export const AdminLabScoresPage = () => {
  const { push } = useToastStore();
  const [scores, setScores] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedStudent, setSelectedStudent] = useState<any>(null);
  const [form, setForm] = useState({ score: 0, reason: "" });

  const loadData = () => {
    labScoresApi.getAllScores()
      .then(setScores)
      .catch(() => push({ title: "Ошибка загрузки", variant: "error" }))
      .finally(() => setIsLoading(false));
  };

  useEffect(() => { loadData(); }, [push]);

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || !e.target.files[0]) return;
    try {

      await labScoresApi.importScores(e.target.files[0]);
      push({ title: "Импорт успешен", variant: "success" });
      loadData();
    } catch (err) {
      push({ title: "Ошибка импорта", variant: "error" });
    }
    e.target.value = "";
  };

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    try {

      await labScoresApi.setScore(selectedStudent.studentId, form.score, form.reason);
      push({ title: "Баллы обновлены", variant: "success" });
      setIsModalOpen(false);
      loadData();
    } catch (err) {
      push({ title: "Ошибка", variant: "error" });
    }
  };

  const filtered = scores.filter(s => s.studentName.toLowerCase().includes(search.toLowerCase()));

  if (isLoading) return <Loader label="Загрузка баллов..." />;

  return (
    <div className="page-stack">
      <PageHeader
        title="Баллы за лабораторные"
        subtitle="Учет баллов"
        actions={
          <label className="ui-button">
            Импорт баллов
            <input type="file" style={{display: "none"}} onChange={handleImport} />
          </label>
        }
      />
      <Card><Input label="Поиск" value={search} onChange={(e) => setSearch(e.target.value)} /></Card>
      <DataTable
        columns={[
          { key: "studentName", title: "Студент" },
          { key: "groupName", title: "Группа" },
          { key: "score", title: "Баллы" },
          { key: "status", title: "Статус", render: (s) => <Badge tone={s.status === "active" ? "success" : "neutral"}>{s.status}</Badge> },
          { key: "actions", title: "Действия", render: (s) => <Button onClick={() => { setSelectedStudent(s); setForm({ score: s.score, reason: "" }); setIsModalOpen(true); }}>Изменить</Button> }
        ]}
        rows={filtered}
      />
      <Modal open={isModalOpen} title="Изменение баллов" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleAdjust}>
          <Input label="Баллы" type="number" value={String(form.score)} onChange={(e) => setForm(c => ({...c, score: Number(e.target.value)}))} required />
          <label className="ui-field">
            <span className="ui-field__label">Основание / Причина</span>
            <textarea className="ui-input ui-textarea" value={form.reason} onChange={(e) => setForm(c => ({...c, reason: e.target.value}))} required />
          </label>
          <Button type="submit">Сохранить</Button>
        </form>
      </Modal>
    </div>
  );
};

export const AdminTestsPage = () => <AdminTestsManagerPage />;
export const AdminCategoriesPage = () => <AdminSectionPage config={sectionConfigs.categories} />;
export const AdminTasksPage = () => <AdminTasksManagerPage />;
export const AdminManualReviewsPage = () => {
  const { push } = useToastStore();
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [students, setStudents] = useState<Student[]>([]);
  const [challenges, setChallenges] = useState<Challenge[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAttempt, setSelectedAttempt] = useState<Attempt | null>(null);
  
  const [score, setScore] = useState(0);
  const [multiplier, setMultiplier] = useState(1.0);

  const loadData = async () => {
    try {
      const [pending, studs, chals] = await Promise.all([
        adminAttemptsApi.getPendingReviews(),
        studentsApi.getAll().catch(() => [] as Student[]),
        adminChallengesApi.getAll().catch(() => [] as Challenge[]),
      ]);
      setAttempts(pending);
      setStudents(studs);
      setChallenges(chals);
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить данные для ручной проверки",
        variant: "error",
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [push]);

  const handleGradeClick = (attempt: Attempt) => {
    setSelectedAttempt(attempt);
    const chal = challenges.find(c => c.id === attempt.challengeId);
    setScore(chal ? chal.points : 100);
    setMultiplier(1.0);
    setIsModalOpen(true);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedAttempt) return;
    setIsSaving(true);

    try {
      await adminAttemptsApi.gradeAttempt(selectedAttempt.id, {
        score,
        manualPercentMultiplier: multiplier,
      });
      push({ title: "Попытка успешно оценена", variant: "success" });
      setIsModalOpen(false);
      setSelectedAttempt(null);
      void loadData();
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить оценку",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем очередь проверок..." />;
  }

  const studentMap = new Map(students.map(s => [s.id, s]));
  const challengeMap = new Map(challenges.map(c => [c.id, c]));

  return (
    <div className="page-stack">
      <PageHeader
        title="Ручные проверки"
        subtitle="Очередь решений (файлов), требующих ручной оценки и подтверждения администратора."
      />

      <div className="metric-grid">
        <Card>
          <div className="metric-card">
            <span className="muted">В очереди</span>
            <strong>{attempts.length}</strong>
          </div>
        </Card>
      </div>

      {attempts.length > 0 ? (
        <DataTable
          columns={[
            {
              key: "challenge",
              title: "Задание",
              render: (attempt) => {
                const chal = challengeMap.get(attempt.challengeId);
                return chal ? `${chal.title} (${chal.points} pts)` : attempt.challengeId;
              }
            },
            {
              key: "student",
              title: "Студент",
              render: (attempt) => {
                const stud = studentMap.get(attempt.studentId);
                return stud ? `${stud.fullName} (${stud.nickname})` : attempt.studentId;
              }
            },
            {
              key: "submittedAt",
              title: "Дата отправки",
              render: (attempt) => new Date(attempt.submittedAt).toLocaleString("ru-RU")
            },
            {
              key: "actions",
              title: "Действие",
              render: (attempt) => (
                <Button onClick={() => handleGradeClick(attempt)}>Оценить</Button>
              )
            }
          ]}
          rows={attempts}
        />
      ) : (
        <Card>
          <p className="muted">Очередь проверок пуста. Все файлы успешно оценены.</p>
        </Card>
      )}

      <Modal
        open={isModalOpen}
        title="Оценить решение"
        onClose={() => {
          setIsModalOpen(false);
          setSelectedAttempt(null);
        }}
      >
        {selectedAttempt && (
          <form className="admin-form" onSubmit={handleSubmit}>
            <div style={{ marginBottom: "1rem" }}>
              <strong>Студент:</strong> {studentMap.get(selectedAttempt.studentId)?.fullName || selectedAttempt.studentId}
            </div>
            <div style={{ marginBottom: "1rem" }}>
              <strong>Задание:</strong> {challengeMap.get(selectedAttempt.challengeId)?.title || selectedAttempt.challengeId}
            </div>
            
            <div style={{ marginBottom: "1rem", padding: "0.75rem", border: "1px solid var(--border)", borderRadius: "var(--radius)", background: "var(--card-bg)" }}>
              <strong>Файл решения:</strong>{" "}
              {selectedAttempt.filePath ? (
                <a
                  href={`/api/admin/attempts/download?path=${encodeURIComponent(selectedAttempt.filePath)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ textDecoration: "underline", color: "var(--primary)" }}
                >
                  Скачать файл ({selectedAttempt.filePath.split("/").pop()})
                </a>
              ) : (
                <span className="muted">Путь к файлу не указан</span>
              )}
            </div>

            <Input
              label="Базовый балл за решение"
              type="number"
              min={0}
              value={String(score)}
              onChange={(e) => setScore(Number(e.target.value))}
              required
            />

            <label className="ui-field">
              <span className="ui-field__label">Штрафной/Поощрительный коэффициент</span>
              <select
                className="ui-input"
                value={String(multiplier)}
                onChange={(e) => setMultiplier(Number(e.target.value))}
              >
                <option value="1.0">1.0 (Полный балл)</option>
                <option value="0.5">0.5 (Штраф 50%)</option>
                <option value="0.1">0.1 (Штраф 90%)</option>
              </select>
            </label>

            <div style={{ margin: "1rem 0", fontSize: "1.1rem" }}>
              <strong>Итоговый балл (до учета времени сдачи):</strong>{" "}
              <span style={{ color: "var(--primary)", fontWeight: "bold" }}>{Math.round(score * multiplier)}</span>
            </div>

            <Button type="submit" disabled={isSaving}>
              {isSaving ? "Сохраняем..." : "Подтвердить оценку"}
            </Button>
          </form>
        )}
      </Modal>
    </div>
  );
};
export const AdminRatingPage = () => {
  const { push } = useToastStore();
  const [rows, setRows] = useState<LeaderboardRow[]>([]);
  const [students, setStudents] = useState<Student[]>([]);
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  
  const [form, setForm] = useState({
    studentId: "",
    competitionId: "",
    points: "0",
    reason: "",
  });

  const loadData = async () => {
    try {
      const [leaderboardRows, studs, comps] = await Promise.all([
        leaderboardApi.getAll(),
        studentsApi.getAll().catch(() => [] as Student[]),
        adminCompetitionsApi.getAll().catch(() => [] as Competition[]),
      ]);
      setRows(leaderboardRows);
      setStudents(studs);
      setCompetitions(comps);
      
      setForm((current) => ({
        ...current,
        studentId: current.studentId || studs[0]?.id || "",
        competitionId: current.competitionId || comps[0]?.id || "",
      }));
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить данные рейтинга",
        variant: "error",
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [push]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.studentId) {
      push({ title: "Выберите студента", variant: "error" });
      return;
    }
    if (!form.reason.trim()) {
      push({ title: "Укажите причину корректировки", variant: "error" });
      return;
    }

    setIsSaving(true);

    try {
      await scoreAdjustmentsApi.createAdjustment({
        studentId: form.studentId,
        competitionId: form.competitionId || undefined,
        points: Number(form.points),
        reason: form.reason,
      });

      push({ title: "Баллы успешно скорректированы", variant: "success" });
      setIsModalOpen(false);
      setForm({
        studentId: students[0]?.id || "",
        competitionId: competitions[0]?.id || "",
        points: "0",
        reason: "",
      });
      void loadData();
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось скорректировать баллы",
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем рейтинг..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Рейтинг"
        subtitle="Административный вид рейтинга с возможностью ручной корректировки баллов участников."
        actions={<Button onClick={() => setIsModalOpen(true)}>Корректировка баллов</Button>}
      />

      <div className="metric-grid">
        <Card>
          <div className="metric-card">
            <span className="muted">Участников</span>
            <strong>{rows.length}</strong>
          </div>
        </Card>
        <Card>
          <div className="metric-card">
            <span className="muted">Средний балл</span>
            <strong>
              {rows.length > 0
                ? Math.round(rows.reduce((sum, r) => sum + r.score, 0) / rows.length)
                : 0}
            </strong>
          </div>
        </Card>
      </div>

      <DataTable
        columns={[
          { key: "place", title: "Место" },
          { key: "participant", title: "Студент" },
          { key: "group", title: "Группа" },
          { key: "score", title: "Баллы" },
          { key: "solved", title: "Решено задач" },
          { key: "v1", title: "v1" },
          { key: "v2", title: "v2" },
          { key: "sCoefficient", title: "S" },
          { key: "recommendedGrade", title: "Рекомендованная оценка" },
        ]}
        rows={rows}
      />

      <Modal open={isModalOpen} title="Корректировка баллов" onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleSubmit}>
          <label className="ui-field">
            <span className="ui-field__label">Студент</span>
            <select
              className="ui-input"
              value={form.studentId}
              onChange={(e) => setForm((current) => ({ ...current, studentId: e.target.value }))}
              required
            >
              <option value="" disabled>-- Выберите студента --</option>
              {students.map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName} ({student.nickname})
                </option>
              ))}
            </select>
          </label>

          <label className="ui-field">
            <span className="ui-field__label">Соревнование</span>
            <select
              className="ui-input"
              value={form.competitionId}
              onChange={(e) => setForm((current) => ({ ...current, competitionId: e.target.value }))}
            >
              <option value="">Не связано с соревнованием</option>
              {competitions.map((comp) => (
                <option key={comp.id} value={comp.id}>
                  {comp.title}
                </option>
              ))}
            </select>
          </label>

          <Input
            label="Баллы (положительное или отрицательное число)"
            type="number"
            value={form.points}
            onChange={(e) => setForm((current) => ({ ...current, points: e.target.value }))}
            required
          />

          <label className="ui-field">
            <span className="ui-field__label">Причина корректировки</span>
            <textarea
              className="ui-input ui-textarea"
              value={form.reason}
              onChange={(e) => setForm((current) => ({ ...current, reason: e.target.value }))}
              placeholder="Укажите причину начисления/списания баллов..."
              required
            />
          </label>

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохраняем..." : "Сохранить"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
export const AdminPromoCodesPage = () => <AdminPromoCodesManagerPage />;
export const AdminSanctionsPage = () => <AdminSectionPage config={sectionConfigs.sanctions} />;

export const AdminActionLogPage = () => {
  const { push } = useToastStore();
  const [logs, setLogs] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    auditLogsApi.getAll()
      .then(setLogs)
      .catch(() => push({ title: "Ошибка загрузки логов", variant: "error" }))
      .finally(() => setIsLoading(false));
  }, [push]);

  const filtered = logs.filter(l =>
    Object.values(l).some(v => String(v).toLowerCase().includes(search.toLowerCase()))
  );

  if (isLoading) return <Loader label="Загрузка логов..." />;

  return (
    <div className="page-stack">
      <PageHeader title="Журнал действий" subtitle="Аудит действий администраторов и системы." />
      <Card><Input label="Поиск" value={search} onChange={(e) => setSearch(e.target.value)} /></Card>
      <DataTable
        columns={[
          { key: "timestamp", title: "Дата/Время", render: (l) => new Date(l.timestamp).toLocaleString("ru") },
          { key: "user", title: "Пользователь" },
          { key: "role", title: "Роль" },
          { key: "action", title: "Действие" },
          { key: "target", title: "Объект" },
          { key: "oldValue", title: "Старое значение" },
          { key: "newValue", title: "Новое значение" },
          { key: "ip", title: "IP" }
        ]}
        rows={filtered}
      />
    </div>
  );
};


export const AdminReportsPage = () => {
  const { push } = useToastStore();

  const handleDownload = async (action: () => Promise<void>, name: string) => {
    try {
      await action();
      push({ title: `Скачивание ${name} началось`, variant: "success" });
    } catch (e) {
      push({ title: "Ошибка скачивания", variant: "error" });
    }
  };

  return (
    <div className="page-stack">
      <PageHeader title="Отчеты" subtitle="Выгрузки по студентам, логам и оценкам." />
      <div className="grid grid-2">
        <Card>
          <div className="page-stack">
            <h3>Итоговый отчет студентов</h3>
            <Button onClick={async () => { handleDownload(reportsApi.downloadFinalStudentReport, "отчета"); }}>Скачать Excel</Button>
          </div>
        </Card>
        <Card>
          <div className="page-stack">
            <h3>Журнал решений</h3>
            <Button onClick={async () => { handleDownload(reportsApi.downloadSolutionsLog, "лога"); }}>Скачать CSV</Button>
          </div>
        </Card>
        <Card>
          <div className="page-stack">
            <h3>Ручные проверки</h3>
            <Button onClick={async () => { handleDownload(reportsApi.downloadManualFileReviews, "проверок"); }}>Скачать PDF</Button>
          </div>
        </Card>
        <Card>
          <div className="page-stack">
            <h3>История корректировок</h3>
            <Button onClick={async () => { handleDownload(reportsApi.downloadAdminAdjustmentsHistory, "корректировок"); }}>Скачать CSV</Button>
          </div>
        </Card>
      </div>
    </div>
  );
};


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

export const AdminGradingScalePage = () => {
  const { push } = useToastStore();
  const [scales, setScales] = useState<GradingScale[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  const [form, setForm] = useState<GradingScalePayload>({
    minCoefficient: 0,
    maxCoefficient: 0,
    grade: 2,
    description: "",
  });

  const loadData = async () => {
    setIsLoading(true);
    try {
      const data = await gradingScalesApi.getAll();
      setScales(data.sort((a, b) => a.minCoefficient - b.minCoefficient));
    } catch (error) {
      push({ title: "Ошибка загрузки шкал оценок", variant: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [push]);

  const validateScale = (currentForm: GradingScalePayload, skipId: string | null): boolean => {
    if (currentForm.minCoefficient >= currentForm.maxCoefficient) {
      push({ title: "Минимальный коэффициент должен быть меньше максимального", variant: "error" });
      return false;
    }

    const overlap = scales.find((scale) => {
      if (skipId && scale.id === skipId) return false;
      return (
        (currentForm.minCoefficient >= scale.minCoefficient && currentForm.minCoefficient <= scale.maxCoefficient) ||
        (currentForm.maxCoefficient >= scale.minCoefficient && currentForm.maxCoefficient <= scale.maxCoefficient) ||
        (currentForm.minCoefficient <= scale.minCoefficient && currentForm.maxCoefficient >= scale.maxCoefficient)
      );
    });

    if (overlap) {
      push({ title: "Интервалы коэффициентов не должны пересекаться", variant: "error" });
      return false;
    }

    return true;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validateScale(form, editingId)) return;

    setIsSaving(true);
    try {
      if (editingId) {
        await gradingScalesApi.update(editingId, form);
        push({ title: "Шкала успешно обновлена", variant: "success" });
      } else {
        await gradingScalesApi.create(form);
        push({ title: "Шкала успешно создана", variant: "success" });
      }
      setIsModalOpen(false);
      void loadData();
    } catch (error) {
      push({ title: "Ошибка сохранения шкалы", variant: "error" });
    } finally {
      setIsSaving(false);
    }
  };

  const handleEdit = (scale: GradingScale) => {
    setEditingId(scale.id);
    setForm({
      minCoefficient: scale.minCoefficient,
      maxCoefficient: scale.maxCoefficient,
      grade: scale.grade,
      description: scale.description || "",
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Вы уверены, что хотите удалить эту шкалу?")) return;
    try {
      await gradingScalesApi.delete(id);
      push({ title: "Шкала удалена", variant: "success" });
      void loadData();
    } catch (error) {
      push({ title: "Ошибка удаления шкалы", variant: "error" });
    }
  };

  const openCreateModal = () => {
    setEditingId(null);
    setForm({ minCoefficient: 0, maxCoefficient: 0, grade: 2, description: "" });
    setIsModalOpen(true);
  };

  if (isLoading) return <Loader label="Загружаем шкалы оценок..." />;

  const tableRows = scales.map((scale) => ({
    ...scale,
    actions: (
      <div className="table-actions">
        <Button onClick={() => handleEdit(scale)} variant="ghost">Редактировать</Button>
        <Button onClick={() => handleDelete(scale.id)} variant="danger">Удалить</Button>
      </div>
    ),
  }));

  return (
    <div className="page-stack">
      <PageHeader
        title="Шкала оценок"
        subtitle="Настройка интервалов для перевода S-коэффициента в оценку"
        actions={<Button onClick={openCreateModal}>Создать интервал</Button>}
      />

      <DataTable
        columns={[
          { key: "minCoefficient", title: "Min S" },
          { key: "maxCoefficient", title: "Max S" },
          { key: "grade", title: "Оценка" },
          { key: "description", title: "Описание" },
          { key: "actions", title: "Действия" },
        ]}
        rows={tableRows}
      />

      <Modal open={isModalOpen} title={editingId ? "Редактирование интервала" : "Создание интервала"} onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleSubmit}>
          <Input
            label="Мин. S"
            type="number"
            step="0.01"
            value={form.minCoefficient}
            onChange={(e) => setForm((curr) => ({ ...curr, minCoefficient: parseFloat(e.target.value) }))}
            required
          />
          <Input
            label="Макс. S"
            type="number"
            step="0.01"
            value={form.maxCoefficient}
            onChange={(e) => setForm((curr) => ({ ...curr, maxCoefficient: parseFloat(e.target.value) }))}
            required
          />
          <Input
            label="Оценка (например, 2, 3, 4, 5)"
            type="number"
            value={form.grade}
            onChange={(e) => setForm((curr) => ({ ...curr, grade: parseInt(e.target.value, 10) }))}
            required
          />
          <Input
            label="Описание"
            type="text"
            value={form.description}
            onChange={(e) => setForm((curr) => ({ ...curr, description: e.target.value }))}
          />

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохранение..." : "Сохранить"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
