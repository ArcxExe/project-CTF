import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

const participantCompetition = {
  title: "Intro CTF Demo",
  status: "Активно",
  phase: "Основной этап",
  startsAt: "26.04.2026, 10:00",
  endsAt: "26.04.2026, 18:00",
  description:
    "Учебное соревнование с тестовым допуском, основным CTF-этапом, рейтингом и промокодами.",
};

const testQuestions = [
  {
    title: "Основы веб-безопасности",
    description: "HTTP, cookies, базовые уязвимости и безопасная отправка флагов.",
    progress: 80,
  },
  {
    title: "Linux и командная строка",
    description: "Права доступа, поиск файлов, пайпы и работа с текстом.",
    progress: 45,
  },
  {
    title: "Криптография warmup",
    description: "Кодировки, простые шифры и внимательность к формату ответа.",
    progress: 20,
  },
];

const tasks = [
  {
    id: "warmup-crypto",
    title: "Warmup Crypto",
    category: "Crypto",
    points: 100,
    status: "Решено",
    difficulty: "easy",
    description: "Найти флаг в простом шифре и отправить его в формате CTF{...}.",
  },
  {
    id: "web-cookie",
    title: "Cookie Jar",
    category: "Web",
    points: 250,
    status: "В работе",
    difficulty: "medium",
    description: "Разобраться с ролью cookie и получить доступ к скрытой странице.",
  },
  {
    id: "forensics-log",
    title: "Lost Log",
    category: "Forensics",
    points: 300,
    status: "Открыто",
    difficulty: "medium",
    description: "Восстановить последовательность событий по журналу сервера.",
  },
];

const ratingRows = [
  { place: 1, participant: "bytebird", group: "ИВТ-21", score: 920, solved: 7 },
  { place: 2, participant: "rootfox", group: "ИВТ-21", score: 760, solved: 5 },
  { place: 3, participant: "nmapkid", group: "ИБ-22", score: 610, solved: 4 },
  { place: 4, participant: "shellcat", group: "ИБ-22", score: 450, solved: 3 },
];

export const RegisterPage = () => {
  const { push } = useToastStore();
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [group, setGroup] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    push({
      title: "Заявка на регистрацию подготовлена",
      variant: "success",
    });
  };

  return (
    <div className="auth-page">
      <Card className="auth-card">
        <div className="page-stack">
          <div>
            <h1>Регистрация участника</h1>
            <p className="muted">Создание учебной учетной записи для участия в тестах и CTF.</p>
          </div>

          <form className="page-stack" onSubmit={handleSubmit}>
            <Input label="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
            <Input label="Никнейм" value={nickname} onChange={(event) => setNickname(event.target.value)} />
            <Input label="Группа" value={group} onChange={(event) => setGroup(event.target.value)} />
            <Input
              label="Пароль"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <Button type="submit" fullWidth>
              Зарегистрироваться
            </Button>
          </form>

          <p className="auth-switch">
            Уже есть аккаунт? <Link to="/login">Войти</Link>
          </p>
        </div>
      </Card>
    </div>
  );
};

export const ParticipantCompetitionPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Страница соревнования"
      subtitle="Обзор этапов, сроков, допуска и быстрых переходов участника."
      actions={<Badge tone="success">{participantCompetition.status}</Badge>}
    />

    <div className="grid grid-2">
      <Card>
        <div className="page-stack">
          <div>
            <h3>{participantCompetition.title}</h3>
            <p className="muted">{participantCompetition.description}</p>
          </div>
          <div className="info-grid">
            <span>Старт</span>
            <strong>{participantCompetition.startsAt}</strong>
            <span>Финиш</span>
            <strong>{participantCompetition.endsAt}</strong>
            <span>Текущий этап</span>
            <strong>{participantCompetition.phase}</strong>
          </div>
        </div>
      </Card>

      <Card>
        <div className="action-grid">
          <Link className="page-link-button" to="/participant/test">
            Перейти к тесту
          </Link>
          <Link className="page-link-button" to="/participant/ctf">
            Открыть CTF
          </Link>
          <Link className="page-link-button" to="/participant/rating">
            Смотреть рейтинг
          </Link>
          <Link className="page-link-button" to="/participant/promo-code">
            Ввести промокод
          </Link>
        </div>
      </Card>
    </div>
  </div>
);

export const ParticipantTestPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Страница теста"
      subtitle="Предварительная проверка знаний перед доступом к основному CTF."
      actions={<Badge tone="info">3 блока</Badge>}
    />

    <div className="grid">
      {testQuestions.map((item) => (
        <Card key={item.title}>
          <div className="test-row">
            <div>
              <h3>{item.title}</h3>
              <p className="muted">{item.description}</p>
            </div>
            <div className="progress-block" aria-label={`Прогресс ${item.progress}%`}>
              <span>{item.progress}%</span>
              <div className="progress-track">
                <div className="progress-fill" style={{ width: `${item.progress}%` }} />
              </div>
            </div>
          </div>
        </Card>
      ))}
    </div>
  </div>
);

export const ParticipantCtfPage = () => (
  <div className="page-stack">
    <PageHeader
      title="Страница CTF"
      subtitle="Задачи, категории, статусы решений и быстрый вход в карточку задания."
      actions={<Badge tone="success">live</Badge>}
    />

    <div className="task-grid">
      {tasks.map((task) => (
        <Card key={task.id}>
          <div className="task-card">
            <div className="task-card__head">
              <Badge tone={task.status === "Решено" ? "success" : "info"}>{task.category}</Badge>
              <strong>{task.points} pts</strong>
            </div>
            <div>
              <h3>{task.title}</h3>
              <p className="muted">{task.description}</p>
            </div>
            <div className="task-card__footer">
              <span>{task.status}</span>
              <Link to={`/participant/tasks/${task.id}`}>Открыть</Link>
            </div>
          </div>
        </Card>
      ))}
    </div>
  </div>
);

export const ParticipantTaskPage = () => {
  const { taskId } = useParams();
  const { push } = useToastStore();
  const [flag, setFlag] = useState("");
  const task = useMemo(() => tasks.find((item) => item.id === taskId) ?? tasks[0], [taskId]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    push({
      title: "Флаг отправлен",
      variant: "success",
    });
    setFlag("");
  };

  return (
    <div className="page-stack">
      <PageHeader
        title="Карточка задания"
        subtitle="Описание, метаданные, вложения, подсказки и отправка флага."
        actions={<Badge tone="info">{task.difficulty}</Badge>}
      />

      <div className="grid grid-2">
        <Card>
          <div className="page-stack">
            <div className="task-card__head">
              <Badge tone="neutral">{task.category}</Badge>
              <strong>{task.points} pts</strong>
            </div>
            <div>
              <h2>{task.title}</h2>
              <p className="muted">{task.description}</p>
            </div>
            <div className="hint-box">
              <strong>Материалы</strong>
              <span>Архив задания, endpoint, история попыток и подсказки появятся здесь.</span>
            </div>
          </div>
        </Card>

        <Card>
          <form className="page-stack" onSubmit={handleSubmit}>
            <Input
              label="Флаг"
              placeholder="CTF{...}"
              value={flag}
              onChange={(event) => setFlag(event.target.value)}
            />
            <Button type="submit">Отправить флаг</Button>
          </form>
        </Card>
      </div>
    </div>
  );
};

export const ParticipantRatingPage = () => (
  <div className="page-stack">
    <PageHeader title="Рейтинг" subtitle="Таблица результатов участников текущего соревнования." />
    <DataTable
      columns={[
        { key: "place", title: "Место" },
        { key: "participant", title: "Участник" },
        { key: "group", title: "Группа" },
        { key: "score", title: "Баллы" },
        { key: "solved", title: "Решено" },
      ]}
      rows={ratingRows}
    />
  </div>
);

export const PromoCodePage = () => {
  const { push } = useToastStore();
  const [code, setCode] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    push({
      title: "Промокод принят в обработку",
      variant: "success",
    });
    setCode("");
  };

  return (
    <div className="page-stack">
      <PageHeader
        title="Ввод промокода"
        subtitle="Активация бонусных попыток, подсказок или дополнительных баллов."
      />

      <Card>
        <form className="promo-form" onSubmit={handleSubmit}>
          <Input
            label="Промокод"
            placeholder="SPRING-CTF-2026"
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
          />
          <Button type="submit">Активировать</Button>
        </form>
      </Card>
    </div>
  );
};
