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
import { studentsApi } from "@/shared/api/services/students";
import type { Student } from "@/shared/types/education";
import { scoreAdjustmentsApi } from "@/shared/api/services/scoreAdjustments";
import { leaderboardApi } from "@/shared/api/services/leaderboard";
import type { LeaderboardRow } from "@/shared/api/services/leaderboard";
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

  const loadData = async () => {
    try {
      const [groupsData, streamsData] = await Promise.all([
        groupsApi.getAll(),
        streamsApi.getAll().catch(() => [])
      ]);
      setGroups(groupsData);
      setStreams(streamsData);
    } catch (error) {
      push({ title: error instanceof Error ? error.message : "Ошибка загрузки", variant: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [push]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {

      await groupsApi.create({
        name: form.name,
        streamId: form.streamId || undefined,
      });
      await loadData();
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
            await loadData();
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

  const loadData = async () => {
    try {
      const data = await streamsApi.getAll();
      setStreams(data);
    } catch (error) {
      push({ title: "Ошибка загрузки", variant: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [push]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {

      await streamsApi.create(form);
      await loadData();
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
            await loadData();
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

export const AdminTestsPage = () => <AdminTestsManagerPage />;
export const AdminTasksPage = () => <AdminTasksManagerPage />;
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
      const [studs, comps] = await Promise.all([
        studentsApi.getAll().catch(() => [] as Student[]),
        adminCompetitionsApi.getAll().catch(() => [] as Competition[]),
      ]);
      setStudents(studs);
      setCompetitions(comps);
      
      setForm((current) => ({
        ...current,
        studentId: current.studentId || studs[0]?.id || "",
        competitionId: current.competitionId || comps[0]?.id || "",
      }));
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить данные",
        variant: "error",
      });
    }
  };

  useEffect(() => {
    void loadData();

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
          v1: entry.v1 ?? 0,
          v2: entry.v2 ?? 0,
          sCoefficient: entry.sCoefficient ?? 0,
          recommendedGrade: entry.recommendedGrade ?? 2,
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
export { AdminAnalyticsPage } from "./AdminAnalyticsPage";
export { AdminGradingScalePage } from "./AdminGradingScalePage";
