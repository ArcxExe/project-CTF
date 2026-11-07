import { useState } from "react";
import { Modal } from "@/shared/ui/Modal/Modal";
import { Button } from "@/shared/ui/Button/Button";
import { Input } from "@/shared/ui/Input/Input";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { academicResetApi, ResetMode, ResetResultDto } from "@/shared/api/services/academicReset";
import type { Group, Stream } from "@/shared/types/education";
import "./AcademicResetModal.css";

interface AcademicResetModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  streams: Stream[];
  groups: Group[];
}

export const AcademicResetModal = ({
  open,
  onClose,
  onSuccess,
  streams = [],
  groups = [],
}: AcademicResetModalProps) => {
  const { push } = useToastStore();
  const [scopeType, setScopeType] = useState<"ALL" | "STREAM" | "GROUP">("ALL");
  const [selectedStreamId, setSelectedStreamId] = useState<string>("");
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");
  const [mode, setMode] = useState<ResetMode>("PROGRESS_ONLY");
  const [confirmationPhrase, setConfirmationPhrase] = useState<string>("");
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);

  const isPhraseValid =
    confirmationPhrase.trim() === "ОЧИСТИТЬ КУРС" ||
    confirmationPhrase.trim().toUpperCase() === "RESET";

  const isScopeValid =
    scopeType === "ALL" ||
    (scopeType === "STREAM" && Boolean(selectedStreamId)) ||
    (scopeType === "GROUP" && Boolean(selectedGroupId));

  const handleDownloadCsv = async () => {
    if (isDownloading) return;
    setIsDownloading(true);
    try {
      await academicResetApi.downloadMetricsCsv();
      push({ title: "Ведомость успеваемости скачана", variant: "success" });
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Ошибка при скачивании ведомости",
        variant: "error",
      });
    } finally {
      setIsDownloading(false);
    }
  };

  const handleReset = async () => {
    if (!isPhraseValid || !isScopeValid || isSubmitting) return;

    if (scopeType === "STREAM" && !selectedStreamId) {
      push({ title: "Выберите поток для очистки", variant: "error" });
      return;
    }

    if (scopeType === "GROUP" && !selectedGroupId) {
      push({ title: "Выберите группу для очистки", variant: "error" });
      return;
    }

    setIsSubmitting(true);
    try {
      const result: ResetResultDto = await academicResetApi.resetSemester({
        mode,
        streamId: scopeType === "STREAM" ? selectedStreamId : null,
        groupId: scopeType === "GROUP" ? selectedGroupId : null,
        confirmationPhrase: confirmationPhrase.trim(),
      });

      push({
        title: `Очистка выполнена! Удалено: ${result.deletedStudents} студ., ${result.deletedUsers} польз., ${result.deletedGroups} групп, ${result.deletedAttempts} попыток CTF, ${result.deletedQuizAttempts} тестов, ${result.deletedLabRecords} лаб., ${result.deletedAttendanceRecords ?? 0} посещений, сброшено КТ/токенов: ${result.resetControlPointRecords ?? 0}.`,
        variant: "success",
      });

      setConfirmationPhrase("");
      onSuccess();
      onClose();
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Ошибка при очистке данных",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title="Подготовка к новому семестру (Очистка данных)"
      onClose={onClose}
      size="lg"
    >
      <div className="semester-reset-content">
        <div className="reset-warning-banner">
          <div className="reset-warning-banner__icon">⚠️</div>
          <div className="reset-warning-banner__body">
            <strong>Внимание! Необратимая операция</strong>
            <p>
              Каталог задач CTF, банк тестов и контрольных точек, шкалы оценивания и учётные
              записи преподавателей и администраторов <strong>сохранятся</strong>.
            </p>
            <p>
              Все результаты студентов (попытки сдачи CTF, баллы за квизы, лабораторные работы ЛР 1–8,
              контрольные точки КТ 1–4, токены и журнал посещаемости) будут очищены{" "}
              <strong>безвозвратно</strong> (рекомендуется сначала скачать экспорт ведомости).
            </p>
            <div className="reset-warning-banner__action">
              <Button
                type="button"
                variant="secondary"
                onClick={handleDownloadCsv}
                disabled={isDownloading}
              >
                {isDownloading ? "Загрузка ведомости..." : "Скачать CSV-экспорт ведомости успеваемости"}
              </Button>
            </div>
          </div>
        </div>

        <div className="reset-form-section">
          <label className="ui-field__label">Область очистки</label>
          <div className="reset-radio-group">
            <label className="reset-radio-label">
              <input
                type="radio"
                name="scopeType"
                value="ALL"
                checked={scopeType === "ALL"}
                onChange={() => setScopeType("ALL")}
              />
              <span>Вся платформа (все потоки и группы)</span>
            </label>
            <label className="reset-radio-label">
              <input
                type="radio"
                name="scopeType"
                value="STREAM"
                checked={scopeType === "STREAM"}
                onChange={() => setScopeType("STREAM")}
              />
              <span>Конкретный поток</span>
            </label>
            <label className="reset-radio-label">
              <input
                type="radio"
                name="scopeType"
                value="GROUP"
                checked={scopeType === "GROUP"}
                onChange={() => setScopeType("GROUP")}
              />
              <span>Конкретная группа</span>
            </label>
          </div>

          {scopeType === "STREAM" && (
            <div className="reset-select-wrapper">
              <label className="ui-field__label">Выберите поток</label>
              <select
                className="ui-input"
                value={selectedStreamId}
                onChange={(e) => setSelectedStreamId(e.target.value)}
              >
                <option value="">-- Выберите поток --</option>
                {streams.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {scopeType === "GROUP" && (
            <div className="reset-select-wrapper">
              <label className="ui-field__label">Выберите группу</label>
              <select
                className="ui-input"
                value={selectedGroupId}
                onChange={(e) => setSelectedGroupId(e.target.value)}
              >
                <option value="">-- Выберите группу --</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        <div className="reset-form-section">
          <label className="ui-field__label">Режим очистки</label>
          <div className="reset-radio-group">
            <label className="reset-radio-label">
              <input
                type="radio"
                name="resetMode"
                value="PROGRESS_ONLY"
                checked={mode === "PROGRESS_ONLY"}
                onChange={() => setMode("PROGRESS_ONLY")}
              />
              <div>
                <strong>Сбросить только баллы, попытки и посещаемость (оставить список студентов)</strong>
                <p className="muted-hint">Списки студентов и академические группы сохраняются.</p>
              </div>
            </label>
            <label className="reset-radio-label">
              <input
                type="radio"
                name="resetMode"
                value="FULL_SEMESTER_WIPE"
                checked={mode === "FULL_SEMESTER_WIPE"}
                onChange={() => setMode("FULL_SEMESTER_WIPE")}
              />
              <div>
                <strong style={{ color: "var(--danger)" }}>
                  Полная очистка перед новым набором (удалить студентов, группы и все результаты)
                </strong>
                <p className="muted-hint">
                  Удаляет студентов, связанные учётные записи (кроме преподавателей и администраторов), группы и все
                  результаты.
                </p>
              </div>
            </label>
          </div>
        </div>

        <div className="reset-confirmation-section">
          <label className="ui-field__label">
            Защита от случайного удаления. Введите фразу{" "}
            <code className="reset-phrase-highlight">ОЧИСТИТЬ КУРС</code> для подтверждения:
          </label>
          <Input
            value={confirmationPhrase}
            onChange={(e) => setConfirmationPhrase(e.target.value)}
            placeholder="ОЧИСТИТЬ КУРС"
            autoComplete="off"
          />
        </div>

        <div className="reset-modal-actions">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Отмена
          </Button>
          <Button
            variant="danger"
            onClick={handleReset}
            disabled={!isPhraseValid || !isScopeValid || isSubmitting}
          >
            {isSubmitting ? "Очистка..." : "Подтвердить очистку"}
          </Button>
        </div>
      </div>
    </Modal>
  );
};
