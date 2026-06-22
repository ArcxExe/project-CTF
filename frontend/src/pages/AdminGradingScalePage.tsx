import { useState, useEffect, FormEvent } from "react";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import { Button } from "@/shared/ui/Button/Button";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Modal } from "@/shared/ui/Modal/Modal";
import { Loader } from "@/shared/ui/Loader/Loader";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { gradingScalesApi, GradingScale, GradingScalePayload } from "@/shared/api/services/gradingScales";
import { streamsApi } from "@/shared/api/services/streams";

export const AdminGradingScalePage = () => {
  const { push } = useToastStore();
  const [scales, setScales] = useState<GradingScale[]>([]);
  const [flows, setFlows] = useState<any[]>([]);

  const [selectedFlowId, setSelectedFlowId] = useState<string>("");

  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  const [form, setForm] = useState<GradingScalePayload>({
    flowId: "",
    gradeName: "A",
    minScore: 0,
    maxScore: 100,
  });

  useEffect(() => {
    void loadFlows();
  }, []);

  useEffect(() => {
    void loadData();
  }, [selectedFlowId]);

  const loadFlows = async () => {
    try {
      const f = await streamsApi.getAll();
      setFlows(f);
    } catch (e) {
      push({ title: "Ошибка загрузки потоков", variant: "error" });
    }
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
      const data = await gradingScalesApi.getAll(selectedFlowId || undefined);
      setScales(data.sort((a, b) => a.minScore - b.minScore));
    } catch (error) {
      push({ title: "Ошибка загрузки шкал оценок", variant: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  const validateScale = (currentForm: GradingScalePayload, skipId: string | null): boolean => {
    if (currentForm.minScore >= currentForm.maxScore) {
      push({ title: "Минимальный балл должен быть меньше максимального", variant: "error" });
      return false;
    }

    const overlap = scales.find((scale) => {
      if (skipId && scale.id === skipId) return false;
      return (
        (currentForm.minScore >= scale.minScore && currentForm.minScore <= scale.maxScore) ||
        (currentForm.maxScore >= scale.minScore && currentForm.maxScore <= scale.maxScore) ||
        (currentForm.minScore <= scale.minScore && currentForm.maxScore >= scale.maxScore)
      );
    });

    if (overlap) {
      push({ title: "Интервалы баллов не должны пересекаться", variant: "error" });
      return false;
    }

    return true;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validateScale(form, editingId)) return;

    const payload = {
      ...form,
      flowId: form.flowId || undefined
    };

    setIsSaving(true);
    try {
      if (editingId) {
        await gradingScalesApi.update(editingId, payload);
        push({ title: "Шкала успешно обновлена", variant: "success" });
      } else {
        await gradingScalesApi.create(payload);
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
      flowId: scale.flowId || "",
      minScore: scale.minScore,
      maxScore: scale.maxScore,
      gradeName: scale.gradeName,
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
    setForm({ flowId: selectedFlowId || "", minScore: 0, maxScore: 100, gradeName: "A" });
    setIsModalOpen(true);
  };

  if (isLoading) return <Loader label="Загружаем шкалы оценок..." />;

  const tableRows = scales.map((scale) => ({
    ...scale,
    flowName: scale.flowId ? flows.find(f => f.id === scale.flowId)?.name || "Неизвестно" : "Глобальная",
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
        subtitle="Настройка интервалов для перевода итогового балла в оценку"
        actions={<Button onClick={openCreateModal}>Создать интервал</Button>}
      />

      <Card>
        <div className="page-stack" style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div>
            <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>Фильтр по потоку</label>
            <select
              value={selectedFlowId}
              onChange={(e) => setSelectedFlowId(e.target.value)}
              style={{ padding: '0.5rem', borderRadius: '4px', border: '1px solid #ccc' }}
            >
              <option value="">Все шкалы (Глобальные)</option>
              {flows.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
            </select>
          </div>
        </div>
      </Card>

      <DataTable
        columns={[
          { key: "flowName", title: "Поток" },
          { key: "minScore", title: "Мин. Балл" },
          { key: "maxScore", title: "Макс. Балл" },
          { key: "gradeName", title: "Оценка" },
          { key: "actions", title: "Действия" },
        ]}
        rows={tableRows}
      />

      <Modal open={isModalOpen} title={editingId ? "Редактирование интервала" : "Создание интервала"} onClose={() => setIsModalOpen(false)}>
        <form className="admin-form" onSubmit={handleSubmit}>
          <div>
            <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>Поток</label>
            <select
              value={form.flowId}
              onChange={(e: any) => setForm((curr) => ({ ...curr, flowId: e.target.value }))}
              style={{ padding: '0.5rem', borderRadius: '4px', border: '1px solid #ccc', width: '100%', marginBottom: '1rem' }}
            >
              <option value="">Глобальная (По умолчанию)</option>
              {flows.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
            </select>
          </div>
          <Input
            label="Мин. Балл (0-100)"
            type="number"
            value={form.minScore}
            onChange={(e: any) => setForm((curr) => ({ ...curr, minScore: parseInt(e.target.value, 10) }))}
            required
          />
          <Input
            label="Макс. Балл (0-100)"
            type="number"
            value={form.maxScore}
            onChange={(e: any) => setForm((curr) => ({ ...curr, maxScore: parseInt(e.target.value, 10) }))}
            required
          />
          <Input
            label="Оценка (например, A, B, 5, 4)"
            type="text"
            value={form.gradeName}
            onChange={(e: any) => setForm((curr) => ({ ...curr, gradeName: e.target.value }))}
            required
          />

          <Button type="submit" disabled={isSaving}>
            {isSaving ? "Сохранение..." : "Сохранить"}
          </Button>
        </form>
      </Modal>
    </div>
  );
};
