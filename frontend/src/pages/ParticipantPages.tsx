import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { useAuthStore } from "@/features/auth/model/authStore";
import { attemptsApi } from "@/shared/api/services/attempts";
import type { AttemptHistoryEntry } from "@/shared/api/services/attempts";
import { challengesApi } from "@/shared/api/services/challenges";
import type { Challenge } from "@/shared/api/services/challenges";
import { leaderboardApi } from "@/shared/api/services/leaderboard";
import type { LeaderboardRow } from "@/shared/api/services/leaderboard";
import { promoCodesApi } from "@/shared/api/services/promoCodes";
import { testsApi } from "@/shared/api/services/tests";
import type { CtfTest } from "@/shared/api/services/tests";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
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

export const RegisterPage = () => {
  const { push } = useToastStore();
  const { currentUser, register, isLoading } = useAuthStore();
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");

  if (currentUser) {
    return <Navigate to="/participant/profile" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    try {
      await register({ email, password, fullName: fullName || undefined });
      push({
        title: "Регистрация выполнена",
        variant: "success",
      });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось зарегистрироваться",
        variant: "error",
      });
    }
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
            <Input label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
            <Input
              label="Пароль"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <Button type="submit" fullWidth disabled={isLoading}>
              {isLoading ? "Регистрируем..." : "Зарегистрироваться"}
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

export const ParticipantTestPage = () => {
  const { push } = useToastStore();
  const [tests, setTests] = useState<CtfTest[]>([]);
  const [testChallenges, setTestChallenges] = useState<Record<string, Challenge[]>>({});
  const [flags, setFlags] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [submittingKey, setSubmittingKey] = useState<string | null>(null);

  useEffect(() => {
    void testsApi
      .getPublished()
      .then(async (testRows) => {
        setTests(testRows);
        const entries = await Promise.all(
          testRows.map(async (test) => [test.id, await testsApi.getChallenges(test.id)] as const),
        );
        setTestChallenges(Object.fromEntries(entries));
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить тесты",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const handleSubmit = async (testId: string, challengeId: string) => {
    const key = `${testId}:${challengeId}`;
    const flag = flags[key]?.trim();
    if (!flag) {
      return;
    }

    setSubmittingKey(key);
    try {
      const response = await testsApi.submitChallengeFlag(testId, challengeId, flag);
      push({
        title: response.pointsAdded ? `${response.message}: +${response.pointsAdded} pts` : response.message,
        variant: response.correct ? "success" : "error",
      });
      setFlags((current) => ({ ...current, [key]: "" }));
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось отправить флаг",
        variant: "error",
      });
    } finally {
      setSubmittingKey(null);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем тесты..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Страница теста"
        subtitle="Предварительная проверка знаний перед доступом к основному CTF."
        actions={<Badge tone="info">{tests.length} тестов</Badge>}
      />

      {tests.length > 0 ? (
        <div className="grid">
          {tests.map((test) => (
            <Card key={test.id}>
              <div className="page-stack">
                <div className="test-row">
                  <div>
                    <h3>{test.title}</h3>
                    <p className="muted">{test.description || "Описание теста не указано."}</p>
                  </div>
                  <div className="entity-summary">
                    <span>{test.questionsCount} заданий</span>
                    <span>{test.timeLimitMinutes} мин</span>
                    <span>проходной {test.passingScore}%</span>
                  </div>
                </div>

                {(testChallenges[test.id] ?? []).map((challenge) => {
                  const key = `${test.id}:${challenge.id}`;
                  return (
                    <div className="test-row" key={challenge.id}>
                      <div>
                        <h4>{challenge.title}</h4>
                        <p className="muted">{challenge.description}</p>
                        <div className="entity-summary">
                          <span>{challenge.category}</span>
                          <span>{challenge.points} pts</span>
                          <span>{challenge.difficulty}</span>
                        </div>
                      </div>
                      <form
                        className="admin-form"
                        onSubmit={(event) => {
                          event.preventDefault();
                          void handleSubmit(test.id, challenge.id);
                        }}
                      >
                        <Input
                          label="Флаг"
                          placeholder="CTF{example}"
                          value={flags[key] ?? ""}
                          onChange={(event) =>
                            setFlags((current) => ({ ...current, [key]: event.target.value }))
                          }
                        />
                        <Button type="submit" disabled={submittingKey === key}>
                          {submittingKey === key ? "Проверяем..." : "Отправить"}
                        </Button>
                      </form>
                    </div>
                  );
                })}

                {(testChallenges[test.id] ?? []).length === 0 && (
                  <p className="muted">В тест пока не добавлены опубликованные задания.</p>
                )}
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <p className="muted">Опубликованных тестов пока нет. Создайте и опубликуйте тест в админке.</p>
        </Card>
      )}
    </div>
  );
};

export const ParticipantCtfPage = () => {
  const { push } = useToastStore();
  const [tasks, setTasks] = useState<Challenge[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void challengesApi
      .getAll()
      .then(setTasks)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить задания",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем задания..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Страница CTF"
        subtitle="Задачи, категории и быстрый вход в карточку задания."
        actions={<Badge tone="success">backend live</Badge>}
      />

      {tasks.length > 0 ? (
        <div className="task-grid">
          {tasks.map((task) => (
            <Card key={task.id}>
              <div className="task-card">
                <div className="task-card__head">
                  <Badge tone="info">{task.category}</Badge>
                  <strong>{task.points} pts</strong>
                </div>
                <div>
                  <h3>{task.title}</h3>
                  <p className="muted">{task.description}</p>
                </div>
                <div className="task-card__footer">
                  <span>{task.difficulty}</span>
                  <Link to={`/participant/tasks/${task.id}`}>Открыть</Link>
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <p className="muted">Список заданий пуст. Опубликуйте хотя бы одно задание в admin API.</p>
        </Card>
      )}
    </div>
  );
};

export const ParticipantTaskPage = () => {
  const { taskId } = useParams();
  const { push } = useToastStore();
  const [task, setTask] = useState<Challenge | null>(null);
  const [history, setHistory] = useState<AttemptHistoryEntry[]>([]);
  const [flag, setFlag] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!taskId) {
      return;
    }

    void Promise.all([challengesApi.getById(taskId), attemptsApi.getHistory(taskId)])
      .then(([challenge, attemptHistory]) => {
        setTask(challenge);
        setHistory(attemptHistory);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить задание",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push, taskId]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    if (!task) {
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await challengesApi.submitFlag(task.id, flag);
      push({
        title: response.pointsAdded ? `${response.message}: +${response.pointsAdded} pts` : response.message,
        variant: response.correct ? "success" : "error",
      });
      const attemptHistory = await attemptsApi.getHistory(task.id);
      setHistory(attemptHistory);
      setFlag("");
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось отправить флаг",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем задание..." />;
  }

  if (!task) {
    return <Navigate to="/participant/ctf" replace />;
  }

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
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Отправляем..." : "Отправить флаг"}
            </Button>
          </form>

          <div className="hint-box">
            <strong>История попыток</strong>
            {history.length > 0 ? (
              <ul className="page-list">
                {history.map((item) => (
                  <li key={item.attemptId}>
                    {new Date(item.submittedAt).toLocaleString("ru-RU")} -{" "}
                    {item.correct ? `верно (+${item.earnedPoints})` : "неверно"}
                  </li>
                ))}
              </ul>
            ) : (
              <span>Попыток пока нет.</span>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
};

export const ParticipantRatingPage = () => {
  const { push } = useToastStore();
  const [rows, setRows] = useState<LeaderboardRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void leaderboardApi
      .getAll()
      .then(setRows)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить рейтинг",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем рейтинг..." />;
  }

  return (
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
        rows={rows}
      />
    </div>
  );
};

export const PromoCodePage = () => {
  const { push } = useToastStore();
  const [code, setCode] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    if (!code.trim()) {
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await promoCodesApi.redeem(code);
      push({
        title: response.message,
        variant: response.accepted ? "success" : "error",
      });
      if (response.accepted) {
        setCode("");
      }
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось активировать промокод",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
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
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Активируем..." : "Активировать"}
          </Button>
        </form>
      </Card>
    </div>
  );
};
