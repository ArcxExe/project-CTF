import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { adminCompetitionsApi } from "@/shared/api/services/competitions";
import type { CompetitionPayload } from "@/shared/api/services/competitions";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import type { Competition } from "@/shared/types/competition";
import "./pages.css";

const statusLabels: Record<Competition["status"], string> = {
  draft: "Черновик",
  published: "Опубликовано",
  archived: "Архив",
};

const toIsoDateTime = (value: string) => (value ? new Date(value).toISOString() : undefined);

const initialForm = {
  title: "",
  description: "",
  startsAt: "",
  endsAt: "",
  status: "draft" as Competition["status"],
};

export const CompetitionsPage = () => {
  const [items, setItems] = useState<Competition[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState(initialForm);
  const { push } = useToastStore();

  const loadCompetitions = async () => {
    const response = await adminCompetitionsApi.getAll();
    setItems(response);
  };

  useEffect(() => {
    void loadCompetitions()
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить соревнования",
          variant: "error",
        });
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [push]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    const payload: CompetitionPayload = {
      title: form.title,
      description: form.description || undefined,
      startsAt: toIsoDateTime(form.startsAt),
      endsAt: toIsoDateTime(form.endsAt),
      status: form.status,
    };

    try {
      const created = await adminCompetitionsApi.create(payload);
      setItems((current) => [created, ...current]);
      setForm(initialForm);
      setIsModalOpen(false);
      push({ title: "Соревнование создано", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Не удалось создать соревнование",
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
        actions={<Button onClick={() => setIsModalOpen(true)}>Создать соревнование</Button>}
      />

      {items.length > 0 ? (
        <div className="grid">
          {items.map((competition) => (
            <Card key={competition.id}>
              <div className="competition-card">
                <div>
                  <h3>{competition.title}</h3>
                  <p className="muted">{competition.description || "Описание не указано"}</p>
                </div>

                <div className="competition-card__meta">
                  <Badge tone={competition.status === "published" ? "success" : "info"}>
                    {statusLabels[competition.status]}
                  </Badge>
                  <span>Старт: {new Date(competition.startsAt).toLocaleString("ru-RU")}</span>
                  <span>Финиш: {new Date(competition.endsAt).toLocaleString("ru-RU")}</span>
                  <span>Рейтинг: {competition.ratingVisible ? "вкл" : "выкл"}</span>
                  <span>Промокоды: {competition.promoCodesEnabled ? "вкл" : "выкл"}</span>
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

      <Modal open={isModalOpen} title="Новое соревнование" onClose={() => setIsModalOpen(false)}>
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
                <option value="active">Активно</option>
              </select>
            </label>
          </div>

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохраняем..." : "Создать"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
