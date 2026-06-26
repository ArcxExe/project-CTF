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
import type { CtfTest, QuizAttempt } from "@/shared/api/services/tests";
import { competitionsApi } from "@/shared/api/services/competitions";
import type { Competition } from "@/shared/types/competition";
import { scoreAdjustmentsApi } from "@/shared/api/services/scoreAdjustments";
import type { ScoreAdjustmentResponse } from "@/shared/api/services/scoreAdjustments";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

export const RegisterPage = () => {
  const { push } = useToastStore();
  const { currentUser, register, isLoading } = useAuthStore();
  const [email, setEmail] = useState("");
  const [studentCode, setStudentCode] = useState("");
  const [password, setPassword] = useState("");

  if (currentUser) {
    return <Navigate to="/participant/profile" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    try {
      await register({ email, password, studentCode });
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
            <Input label="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            <Input label="Шифр студента" value={studentCode} onChange={(event) => setStudentCode(event.target.value)} required />
            <Input
              label="Пароль"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
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

export const ParticipantCompetitionPage = () => {
  const { push } = useToastStore();
  const { competitionId } = useParams();
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [tests, setTests] = useState<CtfTest[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void Promise.all([
      competitionsApi.getAll(),
      testsApi.getPublished()
    ])
      .then(([comps, testRows]) => {
        if (competitionId) {
          setCompetitions(comps.filter((c) => c.id === competitionId));
        } else {
          setCompetitions(comps);
        }
        setTests(testRows);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить соревнования",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push, competitionId]);

  if (isLoading) {
    return <Loader label="Загружаем соревнования..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title={competitions.length === 1 ? "Страница соревнования" : "Соревнования"}
        subtitle="Обзор этапов, сроков, допуска и быстрых переходов участника."
      />

      {competitions.length > 0 ? (
        <div className="page-stack">
          {competitions.map((competition) => {
            const linkedTest = tests.find(t => t.competitionId === competition.id);
            const testUrl = linkedTest ? `/participant/test/${linkedTest.id}` : "/participant/test";

            return (
              <div key={competition.id} className="grid grid-2">
                <Card>
                  <div className="page-stack">
                    <div>
                      <h3>{competition.title}</h3>
                      <p className="muted">{competition.description}</p>
                    </div>
                    <div className="info-grid">
                      <span>Старт</span>
                      <strong>{new Date(competition.startsAt).toLocaleString("ru-RU")}</strong>
                      <span>Финиш</span>
                      <strong>{new Date(competition.endsAt).toLocaleString("ru-RU")}</strong>
                      <span>Статус</span>
                      <strong>{competition.status === "published" ? "Активно" : competition.status}</strong>
                    </div>
                  </div>
                </Card>

                <Card>
                  <div className="action-grid">
                    <Link className="page-link-button" to={testUrl}>
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
            );
          })}
        </div>
      ) : (
        <Card>
          <p className="muted">Нет доступных соревнований.</p>
        </Card>
      )}
    </div>
  );
};

export const ParticipantTestPage = () => {
  const { push } = useToastStore();
  const [tests, setTests] = useState<CtfTest[]>([]);
  const [attempts, setAttempts] = useState<QuizAttempt[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void Promise.all([
      testsApi.getPublished(),
      testsApi.getAttempts()
    ])
      .then(([testRows, attemptRows]) => {
        setTests(testRows);
        setAttempts(attemptRows);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить тесты",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем тесты..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Тесты"
        subtitle="Список доступных тестов для прохождения."
        actions={<Badge tone="info">{tests.length} тестов</Badge>}
      />

      {tests.length > 0 ? (
        <div className="grid">
          {tests.map((test) => {
            const attempt = attempts.find(a => a.quizId === test.id);
            const isCompleted = attempt?.status === "COMPLETED";
            const isInProgress = attempt?.status === "IN_PROGRESS";

            let btnText = "Начать тест";
            if (isCompleted) btnText = "Тест пройден";
            else if (isInProgress) btnText = "Продолжить тест";

            return (
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

                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
                    <Button 
                      onClick={() => window.location.href = `/participant/test/${test.id}`}
                      disabled={isCompleted}
                    >
                      {btnText}
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
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
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void competitionsApi.getAll()
      .then((compRows) => {
        setCompetitions(compRows);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить соревнования",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const renderTaskCard = (task: Challenge) => (
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
  );

  if (isLoading) {
    return <Loader label="Загружаем задания..." />;
  }

  const now = new Date();
  const activeCompetitions = competitions.filter(comp => {
    const start = new Date(comp.startsAt);
    const end = new Date(comp.endsAt);
    return comp.status === "active" && start <= now && now <= end;
  });

  const hasAnyActiveTasks = activeCompetitions.some(c => (c.tasks || []).length > 0);

  return (
    <div className="page-stack">
      <PageHeader
        title="Страница CTF"
        subtitle="Задачи, категории и быстрый вход в карточку задания."
        actions={<Badge tone="success">backend live</Badge>}
      />

      {hasAnyActiveTasks ? (
        <div className="page-stack" style={{ gap: '2rem' }}>
          {activeCompetitions.map((comp) => {
            const compTasks = comp.tasks || [];
            if (compTasks.length === 0) return null;

            return (
              <div key={comp.id} className="page-stack" style={{ gap: '1rem' }}>
                <h2 style={{ borderBottom: '1px solid var(--border)', paddingBottom: '0.5rem', marginBottom: '0.5rem' }}>
                  {comp.title}
                </h2>
                {comp.description && (
                  <p className="muted" style={{ marginTop: '-0.5rem', marginBottom: '0.5rem' }}>
                    {comp.description}
                  </p>
                )}
                <div className="task-grid">
                  {compTasks.map(task => renderTaskCard(task))}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <Card>
          <p className="muted">Нет активных соревнований или заданий в данный момент.</p>
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

    // Listen for FIRST_BLOOD
    const eventSource = new EventSource("/api/leaderboard/live");
    eventSource.addEventListener("FIRST_BLOOD", (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        push({
          title: `Участник ${data.username} получил First Blood по задаче "${data.challengeTitle}"! Вы всё еще можете сдать решение, но баллы за скорость уже начислены.`,
          variant: "info",
        });
        
        setTask(current => {
          if (current && current.title === data.challengeTitle) {
            // Update local state to show First Blood is taken
            return { ...current, firstBloodTaken: true };
          }
          return current;
        });
      } catch (err) {
        console.error("Failed to parse FIRST_BLOOD event", err);
      }
    });
    
    return () => {
      eventSource.close();
    };
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
  const { currentUser } = useAuthStore();
  const [rows, setRows] = useState<LeaderboardRow[]>([]);
  const [adjustments, setAdjustments] = useState<ScoreAdjustmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [leaderboardHidden, setLeaderboardHidden] = useState(false);

  useEffect(() => {
    // Check if leaderboard is hidden
    void competitionsApi.getAll()
      .then((comps) => {
        const now = new Date();
        const active = comps.find(comp => {
          const start = new Date(comp.startsAt);
          const end = new Date(comp.endsAt);
          return start <= now && now <= end;
        }) || comps[0];
        
        if (active?.leaderboardHidden) {
          setLeaderboardHidden(true);
          setIsLoading(false);
        }
      })
      .catch((err) => {
        console.error("Failed to load competitions", err);
      });

    void scoreAdjustmentsApi.getAll()
      .then(setAdjustments)
      .catch((err) => console.error("Failed to load score adjustments", err));

    setIsLoading(true);
    const eventSource = new EventSource("/api/leaderboard/live");

    const parseAndSetRows = (data: string) => {
      try {
        const raw = JSON.parse(data);
        const newRows = raw.map((entry: any, index: number) => ({
          place: index + 1,
          participant: entry.username || entry.participant,
          group: entry.groupName ?? "Без группы",
          score: entry.score ?? entry.totalScore ?? 0,
          solved: entry.solvedCount ?? 0,
          v1: entry.v1,
          v2: entry.v2,
          sCoefficient: entry.sCoefficient,
          recommendedGrade: entry.recommendedGrade,
        }));
        setRows(newRows);
        setIsLoading(false);
      } catch (err) {
        console.error("Failed to parse leaderboard data", err);
      }
    };

    eventSource.addEventListener("INIT", (event) => {
      parseAndSetRows((event as MessageEvent).data);
    });

    eventSource.addEventListener("UPDATE", (event) => {
      parseAndSetRows((event as MessageEvent).data);
    });

    eventSource.onerror = (error) => {
      console.error("Leaderboard SSE error", error);
      eventSource.close();
      
      // Fallback to static fetch if SSE fails
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
    };

    return () => {
      eventSource.close();
    };
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем рейтинг..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader title="Рейтинг" subtitle="Таблица результатов участников текущего соревнования." />
      {leaderboardHidden ? (
        <Card>
          <div style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "3rem 1.5rem",
            textAlign: "center",
          }}>
            <span style={{ fontSize: "3rem", marginBottom: "1rem" }}>❄️</span>
            <h3 style={{ fontSize: "1.5rem", marginBottom: "0.5rem" }}>Рейтинг временно заморожен организаторами</h3>
            <p className="muted">Результаты соревнования скрыты. Продолжайте решать задачи!</p>
          </div>
        </Card>
      ) : (
        <DataTable
          columns={[
            { key: "place", title: "Место" },
            { key: "participant", title: "Участник" },
            { key: "group", title: "Группа" },
            { key: "score", title: "Баллы" },
            { key: "solved", title: "Решено" },
            { key: "v1", title: "v1" },
            { key: "v2", title: "v2" },
            { key: "sCoefficient", title: "S" },
            { key: "recommendedGrade", title: "Рекомендованная оценка" },
          ]}
          rows={rows}
          rowClassName={(row) =>
            currentUser &&
            (row.participant === currentUser.username ||
              row.participant === currentUser.email ||
              row.participant === currentUser.fullName)
              ? "highlighted-row"
              : ""
          }
        />
      )}

      <div style={{ marginTop: "2.5rem" }}>
        <PageHeader
          title="История корректировок баллов"
          subtitle="Записи о ручном изменении баллов участников преподавателями."
        />
        <div style={{ marginTop: "1rem" }}>
          <DataTable
            columns={[
              { key: "participant", title: "Участник" },
              { key: "date", title: "Когда" },
              { key: "reason", title: "За что" },
              {
                key: "amount",
                title: "Сколько",
                render: (row: any) => {
                  const isNegative = row.amount.startsWith("-");
                  return (
                    <span style={{ color: isNegative ? "var(--danger)" : "var(--success)", fontWeight: "bold" }}>
                      {row.amount}
                    </span>
                  );
                }
              },
            ]}
            rows={adjustments.map((adj) => ({
              id: adj.id,
              participant: `${adj.studentName} (${adj.username})`,
              date: new Date(adj.createdAt).toLocaleString("ru-RU"),
              reason: adj.reason,
              amount: adj.points > 0 ? `+${adj.points}` : adj.points.toString(),
            }))}
          />
        </div>
      </div>
    </div>
  );
};

export const PromoCodePage = () => {
  const { push } = useToastStore();
  const [code, setCode] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isValidFormat = code.length >= 1;
  const hasInput = code.length > 0;
  const validationColor = !hasInput ? undefined : isValidFormat ? "var(--success)" : "var(--danger)";

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();

    if (!isValidFormat) {
      push({ title: "Слишком короткий промокод", variant: "error" });
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
            style={{ borderColor: validationColor, borderWidth: validationColor ? '2px' : '1px' }}
            error={hasInput && !isValidFormat ? "Минимум 1 символов" : undefined}
          />
          <Button type="submit" disabled={isSubmitting || (hasInput && !isValidFormat)}>
            {isSubmitting ? "Активируем..." : "Активировать"}
          </Button>
        </form>
      </Card>
    </div>
  );
};
