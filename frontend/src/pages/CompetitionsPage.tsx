import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { adminCompetitionsApi, getCompetitionWindowState } from "@/shared/api/services/competitions";
import type { CompetitionPayload } from "@/shared/api/services/competitions";
import { studentsApi } from "@/shared/api/services/students";
import { challengesApi } from "@/shared/api/services/challenges";
import type { Challenge } from "@/shared/api/services/challenges";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import type { Competition } from "@/shared/types/competition";
import type { Student } from "@/shared/types/education";
import "./pages.css";

const statusLabels: Record<Competition["status"], string> = {
  draft: "Черновик",
  published: "Опубликовано",
  archived: "Архив",
  active: "Активно",
  completed: "Завершено",
};

const formatToLocalTime = (isoString?: string | null) => {
  if (!isoString) return "";
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return "";
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const date = String(d.getDate()).padStart(2, "0");
  const hours = String(d.getHours()).padStart(2, "0");
  const minutes = String(d.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${date}T${hours}:${minutes}`;
};

const normalizeDateTimeValue = (raw?: string): string => {
  if (!raw) return "";
  const trimmed = raw.trim();
  const ruMatch = trimmed.match(/^(\d{2})\.(\d{2})\.(\d{4})[,\sT]+(\d{2}):(\d{2})/);
  if (ruMatch) {
    const [, dd, mm, yyyy, hh, min] = ruMatch;
    return `${yyyy}-${mm}-${dd}T${hh}:${min}`;
  }
  const isoSpaceMatch = trimmed.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})/);
  if (isoSpaceMatch) {
    return `${isoSpaceMatch[1]}T${isoSpaceMatch[2]}`;
  }
  return trimmed;
};

const toIsoDateTime = (value: string): string | null => {
  const normalized = normalizeDateTimeValue(value);
  if (!normalized) return null;
  const parsed = new Date(normalized);
  if (isNaN(parsed.getTime())) return null;
  return parsed.toISOString();
};

const initialForm = {
  title: "",
  description: "",
  startsAt: "",
  endsAt: "",
  status: "draft" as Competition["status"],
  sumTestPoints: false,
  leaderboardHidden: false,
  hiddenStudentIds: [] as string[],
};

export const CompetitionsPage = () => {
  const [items, setItems] = useState<Competition[]>([]);
  const [students, setStudents] = useState<Student[]>([]);
  const [allTasks, setAllTasks] = useState<Challenge[]>([]);
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);
  const [taskSearch, setTaskSearch] = useState("");
  const [taskPoolFilter, setTaskPoolFilter] = useState("");
  const [taskBankFilter, setTaskBankFilter] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(initialForm);
  const [formError, setFormError] = useState<string | null>(null);
  const startsAtInputRef = useRef<HTMLInputElement | null>(null);
  const endsAtInputRef = useRef<HTMLInputElement | null>(null);
  const { push } = useToastStore();

  const loadData = async () => {
    const [comps, studs, tasks] = await Promise.all([
      adminCompetitionsApi.getAll(),
      studentsApi.getAll().catch(() => [] as Student[]),
      challengesApi.getAll().catch(() => [] as Challenge[]),
    ]);
    setItems(comps);
    setStudents(studs);
    setAllTasks(tasks);
  };

  useEffect(() => {
    void loadData()
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить данные",
          variant: "error",
        });
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [push]);

  const handleCreateClick = () => {
    setEditingId(null);
    setSelectedTaskIds([]);
    setTaskSearch("");
    setTaskPoolFilter("");
    setTaskBankFilter("");
    setForm(initialForm);
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleEditClick = (competition: Competition) => {
    setEditingId(competition.id);
    setSelectedTaskIds(competition.tasks?.map((t) => t.id) || []);
    setTaskSearch("");
    setTaskPoolFilter("");
    setTaskBankFilter("");
    setForm({
      title: competition.title,
      description: competition.description || "",
      startsAt: formatToLocalTime(competition.startsAt),
      endsAt: formatToLocalTime(competition.endsAt),
      status: competition.status,
      sumTestPoints: competition.sumTestPoints || false,
      leaderboardHidden: competition.leaderboardHidden || false,
      hiddenStudentIds: competition.hiddenStudentIds || [],
    });
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleDeleteClick = async (id: string) => {
    if (!window.confirm("Вы действительно хотите удалить соревнование?")) {
      return;
    }

    try {
      await adminCompetitionsApi.delete(id);
      setItems((current) => current.filter((item) => item.id !== id));
      push({ title: "Соревнование удалено", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось удалить соревнование",
        variant: "error",
      });
    }
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError(null);

    if (startsAtInputRef.current?.validity?.badInput || endsAtInputRef.current?.validity?.badInput) {
      const msg = "Некорректно заполнены поля даты/времени. Проверьте все сегменты даты и времени.";
      setFormError(msg);
      push({ title: msg, variant: "error" });
      return;
    }

    const domStart = startsAtInputRef.current ? startsAtInputRef.current.value : form.startsAt;
    const domEnd = endsAtInputRef.current ? endsAtInputRef.current.value : form.endsAt;
    const rawStart = normalizeDateTimeValue(domStart);
    const rawEnd = normalizeDateTimeValue(domEnd);

    if (rawStart && !toIsoDateTime(rawStart)) {
      const msg = "Некорректная дата начала соревнования";
      setFormError(msg);
      push({ title: msg, variant: "error" });
      return;
    }
    if (rawEnd && !toIsoDateTime(rawEnd)) {
      const msg = "Некорректная дата окончания соревнования";
      setFormError(msg);
      push({ title: msg, variant: "error" });
      return;
    }

    const startIso = toIsoDateTime(rawStart);
    const endIso = toIsoDateTime(rawEnd);

    if (startIso && endIso && new Date(startIso).getTime() > new Date(endIso).getTime()) {
      const msg = "Дата начала соревнования не может быть позже даты окончания";
      setFormError(msg);
      push({ title: msg, variant: "error" });
      return;
    }

    // Sync any direct DOM value into state so displayed values stay consistent if save fails
    if (rawStart !== form.startsAt || rawEnd !== form.endsAt) {
      setForm((current) => ({ ...current, startsAt: rawStart, endsAt: rawEnd }));
    }

    setIsSaving(true);

    const payload: CompetitionPayload = {
      title: form.title,
      description: form.description || undefined,
      startsAt: startIso,
      endsAt: endIso,
      status: form.status,
      sumTestPoints: form.sumTestPoints,
      leaderboardHidden: form.leaderboardHidden,
      hiddenStudentIds: form.hiddenStudentIds,
    };

    try {
      if (editingId) {
        await adminCompetitionsApi.update(editingId, payload);
        await adminCompetitionsApi.linkTasks(editingId, selectedTaskIds);
        push({ title: "Соревнование обновлено", variant: "success" });
      } else {
        const created = await adminCompetitionsApi.create(payload);
        await adminCompetitionsApi.linkTasks(created.id, selectedTaskIds);
        push({ title: "Соревнование создано", variant: "success" });
      }
      await loadData();
      setForm(initialForm);
      setFormError(null);
      setIsModalOpen(false);
      setEditingId(null);
      setSelectedTaskIds([]);
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Не удалось сохранить соревнование";
      setFormError(msg);
      push({
        title: msg,
        variant: "error",
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <Loader label="Загружаем соревнования..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Соревнования"
        subtitle="Список соревнований из backend API и создание новых записей."
        actions={<Button onClick={handleCreateClick}>Создать соревнование</Button>}
      />

      {items.length > 0 ? (
        <div className="grid">
          {items.map((competition) => {
            const {
              isActiveStatus,
              startDate: startD,
              endDate: endD,
              isEnded,
              isNotStarted,
              isWindowOpen,
            } = getCompetitionWindowState(competition);

            let windowBadgeText = "Окно сдачи: открыто";
            if (!isActiveStatus) {
              windowBadgeText = "Окно сдачи: закрыто";
            } else if (isEnded) {
              windowBadgeText = "Окно сдачи: срок истёк";
            } else if (isNotStarted) {
              windowBadgeText = "Окно сдачи: ожидает старта";
            }

            return (
            <Card key={competition.id}>
              <div className="competition-card" style={{ display: "flex", flexDirection: "column", justifyContent: "space-between", height: "100%" }}>
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem", flexWrap: "wrap" }}>
                    <h3>{competition.title}</h3>
                    <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
                      <Badge tone={isActiveStatus ? "success" : "info"}>
                        Статус: {statusLabels[competition.status] || competition.status}
                      </Badge>
                      <Badge tone={isWindowOpen ? "success" : isEnded ? "danger" : "neutral"}>
                        {windowBadgeText}
                      </Badge>
                    </div>
                  </div>
                  <p className="muted" style={{ margin: "0.5rem 0" }}>{competition.description || "Описание не указано"}</p>
                </div>

                <div className="competition-card__meta" style={{ display: "flex", flexDirection: "column", gap: "0.25rem", fontSize: "0.85rem", marginTop: "1rem" }}>
                  <span>📅 Старт: {startD ? startD.toLocaleString("ru-RU") : "Не указан (открыто сразу)"}</span>
                  <span>📅 Финиш: {endD ? endD.toLocaleString("ru-RU") : "Без ограничения по времени"}</span>
                  <span>🕒 Календарное состояние: {windowBadgeText}</span>
                  <span>📝 Тесты: {competition.sumTestPoints ? "Суммировать баллы" : "Не суммировать"}</span>
                  <span>📊 Лидерборд: {competition.leaderboardHidden ? "Скрыт" : "Показан"}</span>
                  <span>👥 Скрытых студентов: {competition.hiddenStudentIds?.length || 0}</span>
                  <span>🎯 Заданий CTF: {competition.tasks?.length || 0}</span>
                  
                  <div style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}>
                    <Button variant="secondary" onClick={() => handleEditClick(competition)}>Редактировать</Button>
                    <Button variant="danger" onClick={() => void handleDeleteClick(competition.id)}>Удалить</Button>
                  </div>
                </div>
              </div>
            </Card>
            );
          })}
        </div>
      ) : (
        <Card>
          <p className="muted">Пока нет соревнований. Создайте первое соревнование через форму.</p>
        </Card>
      )}

      <Modal open={isModalOpen} title={editingId ? "Редактировать соревнование" : "Новое соревнование"} onClose={() => setIsModalOpen(false)}>
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

          <div className="admin-form-grid">
            <label className="ui-field">
              <span className="ui-field__label">Старт</span>
              <input
                ref={startsAtInputRef}
                className="ui-input"
                type="datetime-local"
                value={form.startsAt}
                onChange={(event) => {
                  setFormError(null);
                  if (event.target.validity?.badInput) return;
                  setForm((current) => ({ ...current, startsAt: normalizeDateTimeValue(event.target.value) }));
                }}
                onInput={(event) => {
                  const target = event.target as HTMLInputElement;
                  setFormError(null);
                  if (target.validity?.badInput) return;
                  setForm((current) => ({
                    ...current,
                    startsAt: normalizeDateTimeValue(target.value),
                  }));
                }}
                onBlur={(event) => {
                  if (event.target.validity?.badInput) return;
                  setForm((current) => ({ ...current, startsAt: normalizeDateTimeValue(event.target.value) }));
                }}
              />
            </label>
            <label className="ui-field">
              <span className="ui-field__label">Финиш</span>
              <input
                ref={endsAtInputRef}
                className="ui-input"
                type="datetime-local"
                value={form.endsAt}
                onChange={(event) => {
                  setFormError(null);
                  if (event.target.validity?.badInput) return;
                  setForm((current) => ({ ...current, endsAt: normalizeDateTimeValue(event.target.value) }));
                }}
                onInput={(event) => {
                  const target = event.target as HTMLInputElement;
                  setFormError(null);
                  if (target.validity?.badInput) return;
                  setForm((current) => ({
                    ...current,
                    endsAt: normalizeDateTimeValue(target.value),
                  }));
                }}
                onBlur={(event) => {
                  if (event.target.validity?.badInput) return;
                  setForm((current) => ({ ...current, endsAt: normalizeDateTimeValue(event.target.value) }));
                }}
              />
            </label>
            <label className="ui-field">
              <span className="ui-field__label">Статус</span>
              <select
                className="ui-input"
                value={form.status}
                onChange={(event) =>
                  setForm((current) => ({ ...current, status: event.target.value as Competition["status"] }))
                }
              >
                <option value="draft">Черновик</option>
                <option value="active">Активно</option>
                <option value="completed">Завершено</option>
              </select>
            </label>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", margin: "1rem 0" }}>
            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={form.sumTestPoints}
                onChange={(e) => setForm((current) => ({ ...current, sumTestPoints: e.target.checked }))}
              />
              <span>Суммировать баллы за тесты</span>
            </label>

            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={form.leaderboardHidden}
                onChange={(e) => setForm((current) => ({ ...current, leaderboardHidden: e.target.checked }))}
              />
              <span>Скрыть лидерборд от участников</span>
            </label>
          </div>

          <label className="ui-field">
            <span className="ui-field__label">Задания CTF (выберите из пула — выбрано: {selectedTaskIds.length})</span>
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", margin: "0.25rem 0" }}>
              <input
                type="text"
                className="ui-input"
                placeholder="Поиск по коду, названию или категории..."
                value={taskSearch}
                onChange={(e) => setTaskSearch(e.target.value)}
                style={{ fontSize: "0.85rem", padding: "0.4rem", flex: 2, minWidth: "180px" }}
              />
              <select
                className="ui-input"
                value={taskPoolFilter}
                onChange={(e) => setTaskPoolFilter(e.target.value)}
                style={{ fontSize: "0.85rem", padding: "0.4rem", width: "auto" }}
              >
                <option value="">Все пулы</option>
                <option value="MAIN">MAIN</option>
                <option value="RESERVE">RESERVE</option>
              </select>
              <select
                className="ui-input"
                value={taskBankFilter}
                onChange={(e) => setTaskBankFilter(e.target.value)}
                style={{ fontSize: "0.85rem", padding: "0.4rem", width: "auto" }}
              >
                <option value="">Все банки</option>
                {Array.from(
                  new Set(["GLOBAL", "G1", "G2", "G3", ...allTasks.map((t) => t.bankId || "GLOBAL")]),
                ).map((bank) => (
                  <option key={bank} value={bank}>
                    {bank}
                  </option>
                ))}
              </select>
            </div>
            {(() => {
              const matchingTasks = allTasks.filter((task) => {
                const matchesText = [task.taskCode, task.title, task.category]
                  .join(" ")
                  .toLowerCase()
                  .includes(taskSearch.toLowerCase());
                const matchesPool = taskPoolFilter
                  ? (task.pool || "MAIN").toUpperCase() === taskPoolFilter
                  : true;
                const matchesBank = taskBankFilter
                  ? (task.bankId || "GLOBAL").toUpperCase() === taskBankFilter
                  : true;
                return matchesText && matchesPool && matchesBank;
              });
              const matchingMainIds = matchingTasks
                .filter((t) => (t.pool || "MAIN").toUpperCase() !== "RESERVE")
                .map((t) => t.id);

              return (
                <>
                  <div style={{ display: "flex", gap: "0.5rem", margin: "0.25rem 0", flexWrap: "wrap" }}>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() =>
                        setSelectedTaskIds((current) => Array.from(new Set([...current, ...matchingMainIds])))
                      }
                    >
                      Выбрать все найденные (MAIN: {matchingMainIds.length})
                    </Button>
                    <Button type="button" variant="secondary" onClick={() => setSelectedTaskIds([])}>
                      Снять выбор
                    </Button>
                  </div>
                  <div className="task-checkbox-list" style={{
                    maxHeight: "180px",
                    overflowY: "auto",
                    border: "1px solid var(--border)",
                    borderRadius: "var(--radius)",
                    padding: "0.5rem",
                    marginTop: "0.25rem",
                    background: "var(--card-bg)"
                  }}>
                    {matchingTasks.map((task) => {
                      const isChecked = selectedTaskIds.includes(task.id);
                      return (
                        <label key={task.id} style={{ display: "flex", alignItems: "center", gap: "0.5rem", padding: "0.25rem 0", cursor: "pointer" }}>
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={(e) => {
                              const checked = e.target.checked;
                              setSelectedTaskIds((current) =>
                                checked
                                  ? [...current, task.id]
                                  : current.filter((id) => id !== task.id)
                              );
                            }}
                          />
                          <span>
                            {task.taskCode ? `[${task.taskCode}] ` : ""}
                            {task.title}{" "}
                            <span className="muted" style={{ fontSize: "0.8rem" }}>
                              ({task.category || "без категории"} · {task.points} pts · {task.pool || "MAIN"}/{task.bankId || "GLOBAL"})
                            </span>
                          </span>
                        </label>
                      );
                    })}
                    {matchingTasks.length === 0 && (
                      <span className="muted">Нет доступных заданий по фильтру</span>
                    )}
                  </div>
                </>
              );
            })()}
          </label>

          <label className="ui-field" style={{ marginTop: "1rem" }}>
            <span className="ui-field__label">Скрыть студентов из рейтинга</span>
            <div className="student-checkbox-list" style={{
              maxHeight: "150px",
              overflowY: "auto",
              border: "1px solid var(--border)",
              borderRadius: "var(--radius)",
              padding: "0.5rem",
              marginTop: "0.25rem",
              background: "var(--card-bg)"
            }}>
              {students.map((student) => {
                const isChecked = form.hiddenStudentIds.includes(student.id);
                return (
                  <label key={student.id} style={{ display: "flex", alignItems: "center", gap: "0.5rem", padding: "0.25rem 0", cursor: "pointer" }}>
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={(e) => {
                        const checked = e.target.checked;
                        setForm((current) => {
                          const hiddenIds = checked
                            ? [...current.hiddenStudentIds, student.id]
                            : current.hiddenStudentIds.filter((id) => id !== student.id);
                          return { ...current, hiddenStudentIds: hiddenIds };
                        });
                      }}
                    />
                    <span>{student.fullName} ({student.nickname})</span>
                  </label>
                );
              })}
              {students.length === 0 && (
                <span className="muted">Нет студентов</span>
              )}
            </div>
          </label>

          {formError && (
            <div
              role="alert"
              style={{
                color: "var(--danger)",
                background: "rgba(239, 68, 68, 0.1)",
                border: "1px solid rgba(239, 68, 68, 0.35)",
                borderRadius: "var(--radius-sm)",
                padding: "0.65rem 0.85rem",
                fontSize: "0.88rem",
                marginTop: "0.75rem",
              }}
            >
              {formError}
            </div>
          )}

          <Button type="submit" disabled={isSaving} style={{ marginTop: "1rem" }}>
            {isSaving ? "Сохраняем..." : (editingId ? "Сохранить" : "Создать")}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
