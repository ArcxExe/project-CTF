import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { adminCompetitionsApi } from "@/shared/api/services/competitions";
import type { CompetitionPayload } from "@/shared/api/services/competitions";
import { studentsApi } from "@/shared/api/services/students";
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
};

const formatToLocalTime = (isoString?: string) => {
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

const toIsoDateTime = (value: string) => (value ? new Date(value).toISOString() : undefined);

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
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(initialForm);
  const { push } = useToastStore();

  const loadData = async () => {
    const [comps, studs] = await Promise.all([
      adminCompetitionsApi.getAll(),
      studentsApi.getAll().catch(() => [] as Student[]),
    ]);
    setItems(comps);
    setStudents(studs);
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
    setForm(initialForm);
    setIsModalOpen(true);
  };

  const handleEditClick = (competition: Competition) => {
    setEditingId(competition.id);
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
    setIsSaving(true);

    const payload: CompetitionPayload = {
      title: form.title,
      description: form.description || undefined,
      startsAt: toIsoDateTime(form.startsAt),
      endsAt: toIsoDateTime(form.endsAt),
      status: form.status,
      sumTestPoints: form.sumTestPoints,
      leaderboardHidden: form.leaderboardHidden,
      hiddenStudentIds: form.hiddenStudentIds,
    };

    try {
      if (editingId) {
        const updated = await adminCompetitionsApi.update(editingId, payload);
        setItems((current) => current.map((item) => (item.id === editingId ? updated : item)));
        push({ title: "Соревнование обновлено", variant: "success" });
      } else {
        const created = await adminCompetitionsApi.create(payload);
        setItems((current) => [created, ...current]);
        push({ title: "Соревнование создано", variant: "success" });
      }
      setForm(initialForm);
      setIsModalOpen(false);
      setEditingId(null);
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить соревнование",
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
          {items.map((competition) => (
            <Card key={competition.id}>
              <div className="competition-card" style={{ display: "flex", flexDirection: "column", justifyContent: "space-between", height: "100%" }}>
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem" }}>
                    <h3>{competition.title}</h3>
                    <Badge tone={competition.status === "published" ? "success" : "info"}>
                      {statusLabels[competition.status]}
                    </Badge>
                  </div>
                  <p className="muted" style={{ margin: "0.5rem 0" }}>{competition.description || "Описание не указано"}</p>
                </div>

                <div className="competition-card__meta" style={{ display: "flex", flexDirection: "column", gap: "0.25rem", fontSize: "0.85rem", marginTop: "1rem" }}>
                  <span>📅 Старт: {new Date(competition.startsAt).toLocaleString("ru-RU")}</span>
                  <span>📅 Финиш: {new Date(competition.endsAt).toLocaleString("ru-RU")}</span>
                  <span>📝 Тесты: {competition.sumTestPoints ? "Суммировать баллы" : "Не суммировать"}</span>
                  <span>📊 Лидерборд: {competition.leaderboardHidden ? "Скрыт" : "Показан"}</span>
                  <span>👥 Скрытых студентов: {competition.hiddenStudentIds?.length || 0}</span>
                  
                  <div style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}>
                    <Button variant="secondary" onClick={() => handleEditClick(competition)}>Редактировать</Button>
                    <Button variant="danger" onClick={() => void handleDeleteClick(competition.id)}>Удалить</Button>
                  </div>
                </div>
              </div>
            </Card>
          ))}
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
            <Input
              label="Старт"
              type="datetime-local"
              value={form.startsAt}
              onChange={(event) => setForm((current) => ({ ...current, startsAt: event.target.value }))}
            />
            <Input
              label="Финиш"
              type="datetime-local"
              value={form.endsAt}
              onChange={(event) => setForm((current) => ({ ...current, endsAt: event.target.value }))}
            />
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
                <option value="published">Опубликовано</option>
                <option value="archived">Архив</option>
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

          <Button type="submit" disabled={isSaving} style={{ marginTop: "1rem" }}>
            {isSaving ? "Сохраняем..." : (editingId ? "Сохранить" : "Создать")}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
