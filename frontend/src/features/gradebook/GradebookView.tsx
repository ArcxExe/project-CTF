import { useEffect, useMemo, useRef, useState } from "react";
import { Card } from "@/shared/ui/Card/Card";
import { Button } from "@/shared/ui/Button/Button";
import { Input } from "@/shared/ui/Input/Input";
import { Modal } from "@/shared/ui/Modal/Modal";
import { Loader } from "@/shared/ui/Loader/Loader";
import { useToastStore } from "@/entities/notification/model/toastStore";
import {
  gradebookApi,
  type GradebookRowDto,
  type LabCellDto,
} from "@/shared/api/services/gradebook";
import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";
import { studentsApi } from "@/shared/api/services/students";
import type { Group, Stream } from "@/shared/types/education";

const LAB_MAX_SCORES: Record<number, { total: number; raw: number; defense: number }> = {
  1: { total: 10, raw: 8, defense: 2 },
  2: { total: 12, raw: 10, defense: 2 },
  3: { total: 12, raw: 10, defense: 2 },
  4: { total: 15, raw: 12, defense: 3 },
  5: { total: 24, raw: 20, defense: 4 },
  6: { total: 28, raw: 24, defense: 4 },
  7: { total: 20, raw: 16, defense: 4 },
  8: { total: 22, raw: 18, defense: 4 },
};

const formatCommaDecimal = (val: number | undefined | null, digits = 2): string => {
  const num = Number(val ?? 0);
  if (!Number.isFinite(num)) return "0,00";
  return num.toFixed(digits).replace(".", ",");
};

interface GradebookViewProps {
  editable?: boolean;
  title?: string;
  subtitle?: string;
}

interface RowDraft {
  pairNumber: string;
  labs: Record<number, string>;
  kt1: string;
  kt2: string;
  kt3: string;
  kt4: string;
  token: string;
  examScore: string;
}

export const GradebookView = ({
  editable = false,
  title = "Электронная ведомость (Рейтинг)",
  subtitle = "Сводная ведомость успеваемости по регламенту курса: ЛР 1–8 (макс. 143), КТ 1–4 (макс. 100), Итог баллов (макс. 243), CTF, Токен, Успеваемость S% и Оценка",
}: GradebookViewProps) => {
  const { push } = useToastStore();

  const [rows, setRows] = useState<GradebookRowDto[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [streams, setStreams] = useState<Stream[]>([]);
  const [selectedStreamId, setSelectedStreamId] = useState<string>("");
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [savingStudentId, setSavingStudentId] = useState<string | null>(null);

  const [drafts, setDrafts] = useState<Record<string, RowDraft>>({});
  const [dirtyRows, setDirtyRows] = useState<Record<string, boolean>>({});

  // Detailed Lab Calculator Modal state
  const [labModal, setLabModal] = useState<{
    open: boolean;
    studentId: string;
    studentName: string;
    labNumber: number;
    rawScore: string;
    defenseScore: string;
    penaltyCoefficient: string;
  } | null>(null);
  const [isSavingLabDetail, setIsSavingLabDetail] = useState(false);

  // Import Modal state
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importGroupId, setImportGroupId] = useState<string>("");
  const [importFile, setImportFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void loadFilters();
  }, []);

  useEffect(() => {
    void loadGradebook();
  }, [selectedGroupId, selectedStreamId]);

  const loadFilters = async () => {
    try {
      const [gList, sList] = await Promise.all([
        groupsApi.getAll().catch(() => []),
        streamsApi.getAll().catch(() => []),
      ]);
      setGroups(gList);
      setStreams(sList);
      if (gList.length > 0 && !importGroupId) {
        setImportGroupId(gList[0].id);
      }
    } catch {
      // Ignore filter load errors for non-admin users
    }
  };

  const initDrafts = (loadedRows: GradebookRowDto[]) => {
    const nextDrafts: Record<string, RowDraft> = {};
    for (const r of loadedRows) {
      const labMap: Record<number, string> = {};
      for (let i = 1; i <= 8; i++) {
        const cell: LabCellDto | undefined = r.labs?.[i];
        labMap[i] = cell ? String(cell.score) : "0";
      }
      nextDrafts[r.studentId] = {
        pairNumber: r.pairNumber != null ? String(r.pairNumber) : "",
        labs: labMap,
        kt1: String(r.kt1 ?? 0),
        kt2: String(r.kt2 ?? 0),
        kt3: String(r.kt3 ?? 0),
        kt4: String(r.kt4 ?? 0),
        token: r.token ?? "",
        examScore: r.examScore != null ? String(r.examScore) : "",
      };
    }
    setDrafts(nextDrafts);
    setDirtyRows({});
  };

  const loadGradebook = async () => {
    setIsLoading(true);
    try {
      const data = await gradebookApi.getGradebook(
        selectedGroupId || undefined,
        selectedStreamId || undefined
      );
      setRows(data);
      initDrafts(data);
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка загрузки ведомости",
        variant: "error",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const updateDraftField = (
    studentId: string,
    updater: (prev: RowDraft) => RowDraft
  ) => {
    setDrafts((curr) => {
      const existing = curr[studentId];
      if (!existing) return curr;
      return {
        ...curr,
        [studentId]: updater(existing),
      };
    });
    setDirtyRows((curr) => ({ ...curr, [studentId]: true }));
  };

  const parseNum = (str: string): number => {
    if (!str || !str.trim()) return 0;
    const n = Number(str.trim().replace(",", "."));
    return Number.isFinite(n) ? n : 0;
  };

  const handleSaveRow = async (studentId: string) => {
    const d = drafts[studentId];
    if (!d) return;
    setSavingStudentId(studentId);
    try {
      const labScores: Record<number, number> = {};
      for (let i = 1; i <= 8; i++) {
        labScores[i] = parseNum(d.labs[i]);
      }
      const pairInt = d.pairNumber.trim() ? parseInt(d.pairNumber.trim(), 10) : null;
      const examInt = d.examScore.trim() ? parseInt(d.examScore.trim(), 10) : null;

      const updated = await gradebookApi.updateStudentRow(studentId, {
        pairNumber: pairInt && pairInt > 0 ? pairInt : 0,
        labScores,
        kt1: parseNum(d.kt1),
        kt2: parseNum(d.kt2),
        kt3: parseNum(d.kt3),
        kt4: parseNum(d.kt4),
        token: d.token,
        examScore: examInt && examInt >= 2 && examInt <= 5 ? examInt : 0,
      });

      setRows((curr) =>
        curr.map((r) => (r.studentId === studentId ? updated : r))
      );
      setDirtyRows((curr) => ({ ...curr, [studentId]: false }));
      push({
        title: `Строка ведомости сохранена (${updated.fullName})`,
        variant: "success",
      });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка сохранения",
        variant: "error",
      });
    } finally {
      setSavingStudentId(null);
    }
  };

  const openLabDetailModal = (row: GradebookRowDto, labNumber: number) => {
    const cell = row.labs?.[labNumber];
    const spec = LAB_MAX_SCORES[labNumber];
    setLabModal({
      open: true,
      studentId: row.studentId,
      studentName: row.fullName,
      labNumber,
      rawScore: String(cell?.rawScore ?? spec.raw),
      defenseScore: String(cell?.defenseScore ?? spec.defense),
      penaltyCoefficient: String(cell?.penaltyCoefficient ?? 1.0),
    });
  };

  const handleSaveLabDetail = async () => {
    if (!labModal) return;
    setIsSavingLabDetail(true);
    try {
      const updated = await gradebookApi.updateLabDetail({
        studentId: labModal.studentId,
        labNumber: labModal.labNumber,
        rawScore: parseNum(labModal.rawScore),
        defenseScore: parseNum(labModal.defenseScore),
        penaltyCoefficient: parseNum(labModal.penaltyCoefficient) || 1.0,
      });
      setRows((curr) =>
        curr.map((r) => (r.studentId === labModal.studentId ? updated : r))
      );
      setDrafts((curr) => {
        const existing = curr[labModal.studentId];
        if (!existing) return curr;
        return {
          ...curr,
          [labModal.studentId]: {
            ...existing,
            labs: {
              ...existing.labs,
              [labModal.labNumber]: String(updated.labs?.[labModal.labNumber]?.score ?? 0),
            },
          },
        };
      });
      setLabModal(null);
      push({
        title: `ЛР ${labModal.labNumber} рассчитана: ${formatCommaDecimal(updated.labs?.[labModal.labNumber]?.score)} б.`,
        variant: "success",
      });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка расчёта ЛР",
        variant: "error",
      });
    } finally {
      setIsSavingLabDetail(false);
    }
  };

  const handleExportCsv = async () => {
    try {
      await gradebookApi.exportCsv(
        selectedGroupId || undefined,
        selectedStreamId || undefined
      );
      push({ title: "Ведомость выгружена в CSV", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка экспорта CSV",
        variant: "error",
      });
    }
  };

  const handleExportXlsx = async () => {
    try {
      await gradebookApi.exportXlsx(
        selectedGroupId || undefined,
        selectedStreamId || undefined
      );
      push({ title: "Ведомость выгружена в XLSX (Excel)", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка экспорта XLSX",
        variant: "error",
      });
    }
  };

  const handleImportSubmit = async () => {
    if (!importFile) {
      push({ title: "Выберите файл .xlsx или .csv", variant: "error" });
      return;
    }
    setIsImporting(true);
    try {
      const targetGroup = importGroupId || selectedGroupId || undefined;
      const res = await studentsApi.importStudents(importFile, targetGroup);
      push({
        title: `Импорт завершён: новых ${res.importedCount}, обновлено ${res.updatedCount}, ЛР: ${res.labsImported}, КТ: ${res.ktImported}`,
        variant: "success",
      });
      setIsImportModalOpen(false);
      setImportFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadGradebook();
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка импорта",
        variant: "error",
      });
    } finally {
      setIsImporting(false);
    }
  };

  const filteredGroups = useMemo(() => {
    if (!selectedStreamId) return groups;
    return groups.filter((g) => g.streamId === selectedStreamId);
  }, [groups, selectedStreamId]);

  const filteredRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (r) =>
        r.fullName.toLowerCase().includes(q) ||
        (r.groupName && r.groupName.toLowerCase().includes(q)) ||
        (r.token && r.token.toLowerCase().includes(q))
    );
  }, [rows, searchQuery]);

  const renderGradeBadge = (grade: number, isEligible: boolean) => {
    const colors: Record<number, { bg: string; border: string; text: string }> = {
      5: { bg: "rgba(34, 197, 94, 0.18)", border: "#22c55e", text: "#4ade80" },
      4: { bg: "rgba(59, 130, 246, 0.18)", border: "#3b82f6", text: "#60a5fa" },
      3: { bg: "rgba(234, 179, 8, 0.18)", border: "#eab308", text: "#facc15" },
      2: { bg: "rgba(239, 68, 68, 0.18)", border: "#ef4444", text: "#f87171" },
    };
    const style = colors[grade] ?? colors[2];
    return (
      <span
        title={
          isEligible
            ? `Допущен (8/8 ЛР). Итоговая оценка: ${grade}`
            : `Не зачтены все 8 обязательных ЛР — оценка 2 (недопуск)`
        }
        style={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          minWidth: 32,
          padding: "2px 8px",
          borderRadius: 6,
          fontWeight: 700,
          background: style.bg,
          border: `1px solid ${style.border}`,
          color: style.text,
        }}
      >
        {grade}
      </span>
    );
  };

  const cellStyle: React.CSSProperties = {
    padding: "6px 8px",
    borderBottom: "1px solid rgba(148, 163, 184, 0.18)",
    borderRight: "1px solid rgba(148, 163, 184, 0.12)",
    textAlign: "center",
    whiteSpace: "nowrap",
    fontSize: "0.85rem",
  };

  const compactInputStyle: React.CSSProperties = {
    width: 54,
    padding: "3px 5px",
    textAlign: "center",
    borderRadius: 4,
    border: "1px solid rgba(148, 163, 184, 0.35)",
    background: "rgba(15, 23, 42, 0.6)",
    color: "inherit",
    fontSize: "0.83rem",
  };

  return (
    <div className="page-stack">
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: "1rem",
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: "1.6rem" }}>{title}</h1>
          <p className="muted" style={{ marginTop: "0.35rem" }}>
            {subtitle}
          </p>
        </div>

        {editable && (
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
            <Button
              variant="secondary"
              onClick={() => {
                if (selectedGroupId) setImportGroupId(selectedGroupId);
                setIsImportModalOpen(true);
              }}
            >
              Импорт (.xlsx / .csv)
            </Button>
            <Button variant="secondary" onClick={handleExportCsv}>
              Экспорт CSV
            </Button>
            <Button onClick={handleExportXlsx}>Экспорт Excel (.xlsx)</Button>
          </div>
        )}
      </div>

      <Card>
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "1rem",
            alignItems: "flex-end",
          }}
        >
          {streams.length > 0 && (
            <div style={{ minWidth: 200 }}>
              <label style={{ display: "block", marginBottom: 4, fontSize: "0.85rem" }}>
                Поток
              </label>
              <select
                className="input"
                value={selectedStreamId}
                onChange={(e) => {
                  setSelectedStreamId(e.target.value);
                  setSelectedGroupId("");
                }}
                style={{ width: "100%", padding: "0.5rem", borderRadius: 6 }}
              >
                <option value="">Все потоки</option>
                {streams.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {groups.length > 0 && (
            <div style={{ minWidth: 200 }}>
              <label style={{ display: "block", marginBottom: 4, fontSize: "0.85rem" }}>
                Учебная группа
              </label>
              <select
                className="input"
                value={selectedGroupId}
                onChange={(e) => setSelectedGroupId(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", borderRadius: 6 }}
              >
                <option value="">Все группы</option>
                {filteredGroups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div style={{ flex: 1, minWidth: 240 }}>
            <Input
              label="Поиск по ФИО, группе или токену"
              placeholder="Например: Абитов Дамир Рамилевич..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <Button variant="ghost" onClick={() => void loadGradebook()}>
            Обновить
          </Button>
        </div>
      </Card>

      {isLoading ? (
        <Loader label="Загружаем ведомость..." />
      ) : (
        <Card>
          <div style={{ overflowX: "auto" }}>
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                minWidth: 1450,
              }}
            >
              <thead>
                <tr
                  style={{
                    background: "rgba(30, 41, 59, 0.85)",
                    borderBottom: "2px solid rgba(148, 163, 184, 0.3)",
                  }}
                >
                  <th style={cellStyle}>№ п/п</th>
                  <th style={cellStyle}>Пара</th>
                  <th style={{ ...cellStyle, textAlign: "left", minWidth: 230 }}>
                    ФИО студента
                  </th>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((labNum) => (
                    <th
                      key={labNum}
                      style={cellStyle}
                      title={`Макс: ${LAB_MAX_SCORES[labNum].total} б. (выполнение ${LAB_MAX_SCORES[labNum].raw} + защита ${LAB_MAX_SCORES[labNum].defense})`}
                    >
                      ЛР {labNum}
                    </th>
                  ))}
                  <th
                    style={{
                      ...cellStyle,
                      background: "rgba(59, 130, 246, 0.14)",
                      fontWeight: 700,
                    }}
                  >
                    Общее ЛР
                  </th>
                  {[1, 2, 3, 4].map((ktNum) => (
                    <th key={ktNum} style={cellStyle} title="Макс: 25,00 б.">
                      КТ {ktNum}
                    </th>
                  ))}
                  <th
                    style={{
                      ...cellStyle,
                      background: "rgba(168, 85, 247, 0.14)",
                      fontWeight: 700,
                    }}
                  >
                    Общее КТ
                  </th>
                  <th
                    style={{
                      ...cellStyle,
                      background: "rgba(234, 179, 8, 0.14)",
                      fontWeight: 700,
                    }}
                    title="Общее ЛР + Общее КТ (макс. 243,00)"
                  >
                    Итог баллов
                  </th>
                  <th style={cellStyle} title="Коэффициент CTF = min(B_CTF / 800, 1,00)">
                    CTF
                  </th>
                  <th style={cellStyle}>Токен</th>
                  <th
                    style={{
                      ...cellStyle,
                      background: "rgba(16, 185, 129, 0.14)",
                      fontWeight: 700,
                    }}
                    title="S = 0.50*L + 0.25*K + 0.25*C"
                  >
                    Успеваемость
                  </th>
                  <th style={{ ...cellStyle, fontWeight: 700 }}>Оценка</th>
                  {editable && <th style={cellStyle}>Действия</th>}
                </tr>
              </thead>

              <tbody>
                {filteredRows.length === 0 ? (
                  <tr>
                    <td
                      colSpan={editable ? 23 : 22}
                      style={{ ...cellStyle, padding: "2rem", textAlign: "center" }}
                    >
                      Нет студентов для отображения. Импортируйте список студентов или выберите другую группу.
                    </td>
                  </tr>
                ) : (
                  filteredRows.map((row, idx) => {
                    const draft = drafts[row.studentId];
                    const isDirty = Boolean(dirtyRows[row.studentId]);
                    return (
                      <tr
                        key={row.studentId}
                        style={{
                          background:
                            idx % 2 === 0
                              ? "transparent"
                              : "rgba(148, 163, 184, 0.04)",
                        }}
                      >
                        <td style={cellStyle}>{row.rowNumber}</td>

                        <td style={cellStyle}>
                          {editable && draft ? (
                            <input
                              type="number"
                              min={1}
                              max={30}
                              style={{ ...compactInputStyle, width: 44 }}
                              value={draft.pairNumber}
                              placeholder="-"
                              onChange={(e) =>
                                updateDraftField(row.studentId, (prev) => ({
                                  ...prev,
                                  pairNumber: e.target.value,
                                }))
                              }
                            />
                          ) : (
                            row.pairNumber ?? ""
                          )}
                        </td>

                        <td style={{ ...cellStyle, textAlign: "left", fontWeight: 500 }}>
                          <div>{row.fullName}</div>
                          <div className="muted" style={{ fontSize: "0.74rem" }}>
                            {row.groupName} • Допуск ЛР: {row.acceptedLabsCount}/8{" "}
                            {row.isEligible ? "✓" : "✗"}
                          </div>
                        </td>

                        {[1, 2, 3, 4, 5, 6, 7, 8].map((labNum) => {
                          const cell = row.labs?.[labNum];
                          const accepted = cell?.accepted ?? false;
                          return (
                            <td
                              key={labNum}
                              style={{
                                ...cellStyle,
                                background: accepted
                                  ? "rgba(34, 197, 94, 0.07)"
                                  : undefined,
                              }}
                            >
                              {editable && draft ? (
                                <div
                                  style={{
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    gap: 2,
                                  }}
                                >
                                  <input
                                    type="number"
                                    step="0.25"
                                    min={0}
                                    max={LAB_MAX_SCORES[labNum].total}
                                    style={compactInputStyle}
                                    value={draft.labs[labNum] ?? "0"}
                                    onChange={(e) =>
                                      updateDraftField(row.studentId, (prev) => ({
                                        ...prev,
                                        labs: {
                                          ...prev.labs,
                                          [labNum]: e.target.value,
                                        },
                                      }))
                                    }
                                  />
                                  <button
                                    type="button"
                                    title="Подробный расчёт ЛР (выполнение + защита + штраф)"
                                    onClick={() => openLabDetailModal(row, labNum)}
                                    style={{
                                      border: "none",
                                      background: "transparent",
                                      color: "#94a3b8",
                                      cursor: "pointer",
                                      padding: "0 2px",
                                      fontSize: "0.78rem",
                                    }}
                                  >
                                    ⚙
                                  </button>
                                </div>
                              ) : (
                                <span
                                  title={
                                    accepted
                                      ? `Зачтено (${formatCommaDecimal(cell?.score)} / ${LAB_MAX_SCORES[labNum].total})`
                                      : `Не зачтено (${formatCommaDecimal(cell?.score)} / ${LAB_MAX_SCORES[labNum].total})`
                                  }
                                >
                                  {formatCommaDecimal(cell?.score)}
                                </span>
                              )}
                            </td>
                          );
                        })}

                        <td
                          style={{
                            ...cellStyle,
                            fontWeight: 700,
                            background: "rgba(59, 130, 246, 0.09)",
                          }}
                          title={`L = ${formatCommaDecimal(row.lPercent)}%`}
                        >
                          <div>{formatCommaDecimal(row.totalLab)}</div>
                          <div className="muted" style={{ fontSize: "0.7rem" }}>
                            {formatCommaDecimal(row.lPercent)}%
                          </div>
                        </td>

                        {([1, 2, 3, 4] as const).map((ktNum) => {
                          const fieldKey = `kt${ktNum}` as "kt1" | "kt2" | "kt3" | "kt4";
                          return (
                            <td key={ktNum} style={cellStyle}>
                              {editable && draft ? (
                                <input
                                  type="number"
                                  step="0.5"
                                  min={0}
                                  max={25}
                                  style={compactInputStyle}
                                  value={draft[fieldKey]}
                                  onChange={(e) =>
                                    updateDraftField(row.studentId, (prev) => ({
                                      ...prev,
                                      [fieldKey]: e.target.value,
                                    }))
                                  }
                                />
                              ) : (
                                formatCommaDecimal(row[fieldKey])
                              )}
                            </td>
                          );
                        })}

                        <td
                          style={{
                            ...cellStyle,
                            fontWeight: 700,
                            background: "rgba(168, 85, 247, 0.09)",
                          }}
                          title={`K = ${formatCommaDecimal(row.kPercent)}%`}
                        >
                          <div>{formatCommaDecimal(row.totalKt)}</div>
                          <div className="muted" style={{ fontSize: "0.7rem" }}>
                            {formatCommaDecimal(row.kPercent)}%
                          </div>
                        </td>

                        <td
                          style={{
                            ...cellStyle,
                            fontWeight: 700,
                            background: "rgba(234, 179, 8, 0.09)",
                          }}
                        >
                          {formatCommaDecimal(row.totalPoints)}
                        </td>

                        <td
                          style={cellStyle}
                          title={`Баллы CTF: ${row.ctfScore} / 800 (${formatCommaDecimal(row.cPercent)}%)`}
                        >
                          <div>{formatCommaDecimal(row.ctfCoeff)}</div>
                          <div className="muted" style={{ fontSize: "0.7rem" }}>
                            {row.ctfScore} б.
                          </div>
                        </td>

                        <td style={cellStyle}>
                          {editable && draft ? (
                            <input
                              type="text"
                              style={{ ...compactInputStyle, width: 76 }}
                              value={draft.token}
                              placeholder="Токен"
                              onChange={(e) =>
                                updateDraftField(row.studentId, (prev) => ({
                                  ...prev,
                                  token: e.target.value,
                                }))
                              }
                            />
                          ) : (
                            row.token || ""
                          )}
                        </td>

                        <td
                          style={{
                            ...cellStyle,
                            fontWeight: 700,
                            background: "rgba(16, 185, 129, 0.09)",
                          }}
                        >
                          {formatCommaDecimal(row.sScore)}
                        </td>

                        <td style={cellStyle}>
                          {renderGradeBadge(row.grade, row.isEligible)}
                        </td>

                        {editable && (
                          <td style={cellStyle}>
                            <Button
                              variant={isDirty ? "primary" : "ghost"}
                              disabled={savingStudentId === row.studentId}
                              onClick={() => void handleSaveRow(row.studentId)}
                            >
                              {savingStudentId === row.studentId
                                ? "..."
                                : isDirty
                                ? "Сохранить*"
                                : "Сохранить"}
                            </Button>
                          </td>
                        )}
                      </tr>
                    );
                  })
                )}
              </tbody>

              <tfoot>
                <tr
                  style={{
                    background: "rgba(30, 41, 59, 0.95)",
                    borderTop: "2px solid rgba(148, 163, 184, 0.4)",
                    fontWeight: 700,
                  }}
                >
                  <td style={cellStyle}></td>
                  <td style={cellStyle}></td>
                  <td style={{ ...cellStyle, textAlign: "left" }}>MAX</td>
                  <td style={cellStyle}>10,00</td>
                  <td style={cellStyle}>12,00</td>
                  <td style={cellStyle}>12,00</td>
                  <td style={cellStyle}>15,00</td>
                  <td style={cellStyle}>24,00</td>
                  <td style={cellStyle}>28,00</td>
                  <td style={cellStyle}>20,00</td>
                  <td style={cellStyle}>22,00</td>
                  <td style={{ ...cellStyle, background: "rgba(59, 130, 246, 0.2)" }}>
                    143,00
                  </td>
                  <td style={cellStyle}>25</td>
                  <td style={cellStyle}>25</td>
                  <td style={cellStyle}>25</td>
                  <td style={cellStyle}>25</td>
                  <td style={{ ...cellStyle, background: "rgba(168, 85, 247, 0.2)" }}>
                    100,00
                  </td>
                  <td style={{ ...cellStyle, background: "rgba(234, 179, 8, 0.2)" }}>
                    243,00
                  </td>
                  <td style={cellStyle}>1,00</td>
                  <td style={cellStyle}></td>
                  <td style={{ ...cellStyle, background: "rgba(16, 185, 129, 0.2)" }}>
                    100,00
                  </td>
                  <td style={cellStyle}>5</td>
                  {editable && <td style={cellStyle}></td>}
                </tr>
              </tfoot>
            </table>
          </div>
        </Card>
      )}

      {/* Detailed Lab Calculator Modal */}
      <Modal
        open={Boolean(labModal?.open)}
        title={
          labModal
            ? `Расчёт ЛР ${labModal.labNumber} по регламенту — ${labModal.studentName}`
            : "Расчёт ЛР"
        }
        onClose={() => setLabModal(null)}
      >
        {labModal && (
          <div className="admin-form">
            <div className="muted" style={{ fontSize: "0.88rem", marginBottom: "0.5rem" }}>
              Норматив ЛР {labModal.labNumber}: выполнение макс.{" "}
              <strong>{LAB_MAX_SCORES[labModal.labNumber].raw} б.</strong> (минимум для зачёта 40% ={" "}
              {(LAB_MAX_SCORES[labModal.labNumber].raw * 0.4).toFixed(1)} б.), защита макс.{" "}
              <strong>{LAB_MAX_SCORES[labModal.labNumber].defense} б.</strong> (минимум для зачёта 50% ={" "}
              {(LAB_MAX_SCORES[labModal.labNumber].defense * 0.5).toFixed(1)} б.). При несданной защите
              итоговый балл ограничивается 60% от максимума.
            </div>

            <Input
              label={`Балл за выполнение (0 – ${LAB_MAX_SCORES[labModal.labNumber].raw})`}
              type="number"
              step="0.5"
              min={0}
              max={LAB_MAX_SCORES[labModal.labNumber].raw}
              value={labModal.rawScore}
              onChange={(e) =>
                setLabModal((curr) =>
                  curr ? { ...curr, rawScore: e.target.value } : null
                )
              }
            />

            <Input
              label={`Балл за индивидуальную защиту (0 – ${LAB_MAX_SCORES[labModal.labNumber].defense})`}
              type="number"
              step="0.5"
              min={0}
              max={LAB_MAX_SCORES[labModal.labNumber].defense}
              value={labModal.defenseScore}
              onChange={(e) =>
                setLabModal((curr) =>
                  curr ? { ...curr, defenseScore: e.target.value } : null
                )
              }
            />

            <div>
              <label style={{ display: "block", marginBottom: 6, fontWeight: 500 }}>
                Коэффициент своевременности сдачи (Раздел 1.2)
              </label>
              <select
                className="input"
                value={labModal.penaltyCoefficient}
                onChange={(e) =>
                  setLabModal((curr) =>
                    curr ? { ...curr, penaltyCoefficient: e.target.value } : null
                  )
                }
                style={{ width: "100%", padding: "0.55rem", borderRadius: 6 }}
              >
                <option value="1">1.00 — Сдано в срок (без штрафа)</option>
                <option value="0.85">0.85 — Опоздание до 7 дней (−15%)</option>
                <option value="0.7">0.70 — Опоздание более 7 дней (−30%)</option>
              </select>
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem", marginTop: "1rem" }}>
              <Button variant="ghost" onClick={() => setLabModal(null)}>
                Отмена
              </Button>
              <Button disabled={isSavingLabDetail} onClick={() => void handleSaveLabDetail()}>
                {isSavingLabDetail ? "Расчёт..." : "Рассчитать и сохранить"}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Import Spreadsheet Modal */}
      <Modal
        open={isImportModalOpen}
        title="Импорт списка студентов и ведомости (.xlsx / .csv)"
        onClose={() => setIsImportModalOpen(false)}
      >
        <div className="admin-form">
          <div className="muted" style={{ fontSize: "0.88rem" }}>
            Поддерживаются файлы Excel (<code>.xlsx</code>) и <code>.csv</code> в формате ведомости{" "}
            <strong>«Рейтинг»</strong> (колонки <code>№ п/п</code>, <code>Пара</code>,{" "}
            <code>ФИО студента</code>, <code>ЛР 1..8</code>, <code>КТ 1..4</code>, <code>Токен</code>), а
            также стандартные списки студентов. Итоговая строка <code>MAX</code> пропускается автоматически.
          </div>

          <div>
            <label style={{ display: "block", marginBottom: 6, fontWeight: 500 }}>
              Целевая учебная группа (если в файле нет колонки «Группа»)
            </label>
            <select
              className="input"
              value={importGroupId}
              onChange={(e) => setImportGroupId(e.target.value)}
              style={{ width: "100%", padding: "0.55rem", borderRadius: 6 }}
            >
              <option value="">Определить из файла / Общая группа</option>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name} {g.streamName ? `(${g.streamName})` : ""}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: "block", marginBottom: 6, fontWeight: 500 }}>
              Файл ведомости (.xlsx или .csv)
            </label>
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.csv"
              onChange={(e) => setImportFile(e.target.files?.[0] ?? null)}
            />
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem", marginTop: "1rem" }}>
            <Button variant="ghost" onClick={() => setIsImportModalOpen(false)}>
              Отмена
            </Button>
            <Button disabled={isImporting || !importFile} onClick={() => void handleImportSubmit()}>
              {isImporting ? "Импортируем..." : "Импортировать"}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
