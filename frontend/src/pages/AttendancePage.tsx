import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { attendanceApi } from "@/shared/api/services/attendance";
import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";
import { cn } from "@/shared/lib/cn";
import {
  ATTENDANCE_CYCLES,
  type AttendanceStatus,
  type SessionType,
  type StudentAttendanceRow,
} from "@/shared/types/attendance";
import type { Group, Stream } from "@/shared/types/education";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";
import "./attendance.css";

type SheetMode = "LECTURES" | "PRACTICES" | "ALL";

interface CycleColumn {
  sessionType: SessionType;
  cycle: number;
  title: string;
}

interface MenuState {
  studentId: string;
  row: StudentAttendanceRow;
  sessionType: SessionType;
  cycleNumber: number;
  x: number;
  y: number;
}

const STATUS_META: Record<AttendanceStatus, { short: string; label: string; tone: string }> = {
  PRESENT: { short: "П", label: "Присутствовал", tone: "present" },
  ABSENT: { short: "Н", label: "Пропуск", tone: "absent" },
  EXCUSED: { short: "У", label: "Уважительная причина", tone: "excused" },
};

/** Click cycle: пусто → П → Н → У → пусто. */
const STATUS_CYCLE: (AttendanceStatus | null)[] = [null, "PRESENT", "ABSENT", "EXCUSED"];

const nextStatus = (current: AttendanceStatus | null): AttendanceStatus | null => {
  const index = STATUS_CYCLE.indexOf(current);
  return STATUS_CYCLE[(index + 1) % STATUS_CYCLE.length];
};

const sessionTitle = (sessionType: SessionType) => (sessionType === "LECTURE" ? "Лекция" : "Практика");

const MODES: { value: SheetMode; label: string }[] = [
  { value: "LECTURES", label: "Лекции (1–8)" },
  { value: "PRACTICES", label: "Практики (1–8)" },
  { value: "ALL", label: "Все (Лек 1..8 + Пр 1..8)" },
];

export const AdminAttendancePage = () => {
  const { push } = useToastStore();

  const [streams, setStreams] = useState<Stream[]>([]);
  const [streamId, setStreamId] = useState("");
  const [groups, setGroups] = useState<Group[]>([]);
  const [metaLoaded, setMetaLoaded] = useState(false);
  const [groupId, setGroupId] = useState("");
  const [mode, setMode] = useState<SheetMode>("LECTURES");
  const [rows, setRows] = useState<StudentAttendanceRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pendingCell, setPendingCell] = useState<string | null>(null);
  const [contextMenu, setContextMenu] = useState<MenuState | null>(null);
  const clickTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [commentModal, setCommentModal] = useState<{
    row: StudentAttendanceRow;
    sessionType: SessionType;
    cycleNumber: number;
    status: AttendanceStatus;
    comment: string;
  } | null>(null);

  useEffect(() => {
    const handleGlobalClick = () => setContextMenu(null);
    window.addEventListener("click", handleGlobalClick);
    return () => {
      window.removeEventListener("click", handleGlobalClick);
      if (clickTimerRef.current) {
        clearTimeout(clickTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    Promise.all([groupsApi.getAll(), streamsApi.getAll()])
      .then(([fetchedGroups, fetchedStreams]) => {
        setGroups(fetchedGroups);
        setStreams(fetchedStreams);
        setGroupId((current) => current || fetchedGroups[0]?.id || "");
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить списки групп и потоков",
          variant: "error",
        });
      })
      .finally(() => setMetaLoaded(true));
  }, [push]);

  const filteredGroups = useMemo(() => {
    if (!streamId) return groups;
    return groups.filter((g) => g.streamId === streamId);
  }, [groups, streamId]);

  const loadSheet = useCallback(async () => {
    setIsLoading(true);
    try {
      const sheet = await attendanceApi.getSheet(groupId || undefined, (!groupId && streamId) ? streamId : undefined);
      setRows(sheet);
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить ведомость посещаемости",
        variant: "error",
      });
    } finally {
      setIsLoading(false);
    }
  }, [groupId, streamId, push]);

  useEffect(() => {
    if (metaLoaded) {
      void loadSheet();
    }
  }, [metaLoaded, loadSheet]);

  const columns = useMemo<CycleColumn[]>(() => {
    const result: CycleColumn[] = [];
    if (mode !== "PRACTICES") {
      ATTENDANCE_CYCLES.forEach((cycle) =>
        result.push({ sessionType: "LECTURE", cycle, title: `Лек ${cycle}` }),
      );
    }
    if (mode !== "LECTURES") {
      ATTENDANCE_CYCLES.forEach((cycle) =>
        result.push({ sessionType: "PRACTICE", cycle, title: `Пр ${cycle}` }),
      );
    }
    return result;
  }, [mode]);

  const applyRowUpdate = (updated: StudentAttendanceRow) => {
    setRows((current) =>
      current.map((item) =>
        item.studentId === updated.studentId
          ? { ...updated, rowNumber: item.rowNumber }
          : item,
      ),
    );
  };

  const handleCellClick = async (row: StudentAttendanceRow, sessionType: SessionType, cycleNumber: number) => {
    const map = sessionType === "LECTURE" ? row.lectures : row.practices;
    const next = nextStatus(map[String(cycleNumber)] ?? null);
    const cellKey = `${row.studentId}:${sessionType}:${cycleNumber}`;

    setPendingCell(cellKey);
    try {
      const updated = await attendanceApi.updateCell({
        studentId: row.studentId,
        sessionType,
        cycleNumber,
        status: next,
      });
      applyRowUpdate(updated);
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить отметку",
        variant: "error",
      });
    } finally {
      setPendingCell(null);
    }
  };

  const handleDirectStatusSelect = async (
    row: StudentAttendanceRow,
    sessionType: SessionType,
    cycleNumber: number,
    status: AttendanceStatus | null,
  ) => {
    const cellKey = `${row.studentId}:${sessionType}:${cycleNumber}`;
    setPendingCell(cellKey);
    setContextMenu(null);
    try {
      const updated = await attendanceApi.updateCell({
        studentId: row.studentId,
        sessionType,
        cycleNumber,
        status,
      });
      applyRowUpdate(updated);
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить статус",
        variant: "error",
      });
    } finally {
      setPendingCell(null);
    }
  };

  const handleCellContextMenu = (
    event: React.MouseEvent,
    row: StudentAttendanceRow,
    sessionType: SessionType,
    cycleNumber: number,
  ) => {
    event.preventDefault();
    setContextMenu({
      studentId: row.studentId,
      row,
      sessionType,
      cycleNumber,
      x: event.clientX,
      y: event.clientY,
    });
  };

  const handleCellSingleClick = (
    event: React.MouseEvent,
    row: StudentAttendanceRow,
    sessionType: SessionType,
    cycleNumber: number,
  ) => {
    if (event.detail > 1) {
      if (clickTimerRef.current) {
        clearTimeout(clickTimerRef.current);
        clickTimerRef.current = null;
      }
      return;
    }
    if (clickTimerRef.current) {
      clearTimeout(clickTimerRef.current);
    }
    clickTimerRef.current = setTimeout(() => {
      clickTimerRef.current = null;
      void handleCellClick(row, sessionType, cycleNumber);
    }, 220);
  };

  const handleCommentEdit = (row: StudentAttendanceRow, sessionType: SessionType, cycleNumber: number) => {
    if (clickTimerRef.current) {
      clearTimeout(clickTimerRef.current);
      clickTimerRef.current = null;
    }
    const commentMap = sessionType === "LECTURE" ? row.lectureComments : row.practiceComments;
    const currentComment = commentMap?.[String(cycleNumber)] ?? "";

    const statusMap = sessionType === "LECTURE" ? row.lectures : row.practices;
    const currentStatus = statusMap[String(cycleNumber)] ?? null;

    setContextMenu(null);
    setCommentModal({
      row,
      sessionType,
      cycleNumber,
      status: currentStatus ?? "EXCUSED",
      comment: currentComment,
    });
  };

  const handleSaveCommentModal = async () => {
    if (!commentModal) return;
    const { row, sessionType, cycleNumber, status, comment } = commentModal;
    const cellKey = `${row.studentId}:${sessionType}:${cycleNumber}`;
    setPendingCell(cellKey);
    try {
      const updated = await attendanceApi.updateCell({
        studentId: row.studentId,
        sessionType,
        cycleNumber,
        status,
        comment,
      });
      applyRowUpdate(updated);
      setCommentModal(null);
      push({ title: "Примечание сохранено", variant: "success" });
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось сохранить примечание",
        variant: "error",
      });
    } finally {
      setPendingCell(null);
    }
  };

  const handleBulkMark = async (sessionType: SessionType, cycleNumber: number) => {
    if (!groupId) {
      push({ title: "Сначала выберите академическую группу", variant: "error" });
      return;
    }

    const cellKey = `bulk:${sessionType}:${cycleNumber}`;
    setPendingCell(cellKey);
    try {
      const sheet = await attendanceApi.bulkMark({
        groupId,
        sessionType,
        cycleNumber,
        defaultStatus: "PRESENT",
      });
      setRows(sheet);
      push({
        title: `${sessionTitle(sessionType)} ${cycleNumber}: группа отмечена присутствующими`,
        variant: "success",
      });
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Не удалось выполнить массовую отметку",
        variant: "error",
      });
    } finally {
      setPendingCell(null);
    }
  };

  const handleExportCsv = async () => {
    try {
      await attendanceApi.exportCsv(
        groupId || undefined,
        !groupId && streamId ? streamId : undefined,
      );
      push({ title: "Журнал посещаемости выгружен в CSV", variant: "success" });
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Ошибка экспорта CSV",
        variant: "error",
      });
    }
  };

  const handleExportXlsx = async () => {
    try {
      await attendanceApi.exportXlsx(
        groupId || undefined,
        !groupId && streamId ? streamId : undefined,
      );
      push({ title: "Журнал посещаемости выгружен в Excel (.xlsx)", variant: "success" });
    } catch (error: unknown) {
      push({
        title: error instanceof Error ? error.message : "Ошибка экспорта XLSX",
        variant: "error",
      });
    }
  };

  if (!metaLoaded || isLoading) {
    return <Loader label="Загружаем ведомость посещаемости..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Посещаемость лекций и практик"
        subtitle="Ведомость посещений: 8 лекций и 8 практических занятий. Посещаемость учитывается в аналитике и не изменяет рейтинговый балл (п. 3.4 регламента)."
        actions={
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            <button
              type="button"
              className="attendance-mode-btn"
              onClick={() => void handleExportCsv()}
            >
              Экспорт CSV
            </button>
            <button
              type="button"
              className="attendance-mode-btn is-active"
              onClick={() => void handleExportXlsx()}
            >
              Экспорт Excel (.xlsx)
            </button>
          </div>
        }
      />

      <Card>
        <div className="attendance-toolbar">
          {streams.length > 0 && (
            <label className="ui-field attendance-group-select">
              <span className="ui-field__label">Учебный поток</span>
              <select
                className="ui-input"
                value={streamId}
                onChange={(event) => {
                  setStreamId(event.target.value);
                  setGroupId("");
                }}
              >
                <option value="">Все потоки</option>
                {streams.map((stream) => (
                  <option key={stream.id} value={stream.id}>
                    {stream.name}
                  </option>
                ))}
              </select>
            </label>
          )}

          <label className="ui-field attendance-group-select">
            <span className="ui-field__label">Академическая группа</span>
            <select className="ui-input" value={groupId} onChange={(event) => setGroupId(event.target.value)}>
              <option value="">Все студенты {streamId ? "потока" : ""}</option>
              {filteredGroups.map((group) => (
                <option key={group.id} value={group.id}>
                  {group.name}
                </option>
              ))}
            </select>
          </label>

          <div className="attendance-mode-switch" role="group" aria-label="Режим отображения колонок">
            {MODES.map((item) => (
              <button
                key={item.value}
                type="button"
                className={cn("attendance-mode-btn", mode === item.value && "is-active")}
                onClick={() => setMode(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>

          <div className="attendance-legend">
            {(Object.keys(STATUS_META) as AttendanceStatus[]).map((status) => (
              <span key={status} className="attendance-legend__item">
                <span className={cn("attendance-chip", `attendance-chip--${STATUS_META[status].tone}`)}>
                  {STATUS_META[status].short}
                </span>
                {STATUS_META[status].label}
              </span>
            ))}
            <span className="attendance-legend__item">
              <span className="attendance-chip">—</span>
              не отмечено
            </span>
          </div>
        </div>
      </Card>

      <div className="attendance-table-wrap">
        <table className="attendance-table">
          <thead>
            <tr>
              <th>№ п/п</th>
              <th>Пара</th>
              <th className="attendance-name">ФИО студента</th>
              {columns.map((column) => (
                <th
                  key={`${column.sessionType}-${column.cycle}`}
                  className={cn("attendance-cyc-col", column.sessionType === "PRACTICE" && "attendance-cyc-col--practice")}
                >
                  <span>{column.title}</span>
                  <button
                    type="button"
                    className="attendance-bulk-btn"
                    disabled={!groupId || pendingCell === `bulk:${column.sessionType}:${column.cycle}`}
                    onClick={() => void handleBulkMark(column.sessionType, column.cycle)}
                    title={`Отметить всех присутствующими (${sessionTitle(column.sessionType)} ${column.cycle})`}
                  >
                    +
                  </button>
                </th>
              ))}
              <th className="attendance-summary-col">Лекции (из 8)</th>
              <th className="attendance-summary-col">Практики (из 8)</th>
              <th className="attendance-summary-col">Уваж.</th>
              <th
                className="attendance-summary-col"
                title="Доля присутствий (П) от числа проведённых (отмеченных в журнале) занятий"
              >
                Итого % (от отм.)
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.length > 0 ? (
              rows.map((row) => {
                const markedLectures = Object.values(row.lectures ?? {}).filter(Boolean).length;
                const markedPractices = Object.values(row.practices ?? {}).filter(Boolean).length;
                const conductedCount = markedLectures + markedPractices;
                const attendedCount = row.lecturesPresentCount + row.practicesPresentCount;
                return (
                <tr key={row.studentId}>
                  <td>{row.rowNumber}</td>
                  <td>{row.pairNumber ?? "—"}</td>
                  <td className="attendance-name">{row.fullName}</td>
                  {columns.map((column) => {
                    const statusMap = column.sessionType === "LECTURE" ? row.lectures : row.practices;
                    const status = statusMap[String(column.cycle)] ?? null;
                    const meta = status ? STATUS_META[status] : null;

                    const commentMap = column.sessionType === "LECTURE" ? row.lectureComments : row.practiceComments;
                    const comment = commentMap?.[String(column.cycle)];
                    const hasComment = Boolean(comment);

                    const cellKey = `${row.studentId}:${column.sessionType}:${column.cycle}`;
                    return (
                      <td key={`${column.sessionType}-${column.cycle}`}>
                        <button
                          type="button"
                          className={cn(
                            "attendance-cell",
                            meta && `attendance-cell--${meta.tone}`,
                            hasComment && "attendance-cell--has-comment",
                          )}
                          disabled={pendingCell === cellKey}
                          onClick={(event) => handleCellSingleClick(event, row, column.sessionType, column.cycle)}
                          onContextMenu={(event) =>
                            handleCellContextMenu(event, row, column.sessionType, column.cycle)
                          }
                          onDoubleClick={() => handleCommentEdit(row, column.sessionType, column.cycle)}
                          title={`${sessionTitle(column.sessionType)} ${column.cycle}: ${
                            meta ? meta.label : "не отмечено"
                          }${comment ? ` [Примечание: ${comment}]` : ""}. Клик — сменить статус, ПКМ — быстрое меню, двойной клик — примечание.`}
                        >
                          {meta ? meta.short : "—"}
                        </button>
                      </td>
                    );
                  })}
                  <td className="attendance-summary">{row.lecturesPresentCount} / 8</td>
                  <td className="attendance-summary">{row.practicesPresentCount} / 8</td>
                  <td className="attendance-summary">{row.excusedCount}</td>
                  <td
                    className="attendance-summary attendance-summary--percent"
                    title={`Присутствовал на ${attendedCount} из ${conductedCount} отмеченных занятий (всего по плану курса: 16)`}
                  >
                    <div>{(row.attendancePercent ?? 0).toFixed(1)}%</div>
                    <div className="muted" style={{ fontSize: "0.72rem", fontWeight: 400 }}>
                      {attendedCount}/{conductedCount} отм.
                    </div>
                  </td>
                </tr>
                );
              })
            ) : (
              <tr>
                <td className="attendance-empty" colSpan={columns.length + 7}>
                  {groupId
                    ? "В выбранной группе нет студентов"
                    : "Нет студентов для отображения"}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {contextMenu && (
        <div
          className="attendance-mini-menu"
          style={{ top: contextMenu.y, left: contextMenu.x }}
          onClick={(e) => e.stopPropagation()}
        >
          <button
            type="button"
            className="attendance-mini-menu__item"
            onClick={() =>
              void handleDirectStatusSelect(
                contextMenu.row,
                contextMenu.sessionType,
                contextMenu.cycleNumber,
                null,
              )
            }
          >
            <span className="attendance-chip">—</span>
            Очистить
          </button>
          <button
            type="button"
            className="attendance-mini-menu__item"
            onClick={() =>
              void handleDirectStatusSelect(
                contextMenu.row,
                contextMenu.sessionType,
                contextMenu.cycleNumber,
                "PRESENT",
              )
            }
          >
            <span className="attendance-chip attendance-chip--present">П</span>
            Присутствовал
          </button>
          <button
            type="button"
            className="attendance-mini-menu__item"
            onClick={() =>
              void handleDirectStatusSelect(
                contextMenu.row,
                contextMenu.sessionType,
                contextMenu.cycleNumber,
                "ABSENT",
              )
            }
          >
            <span className="attendance-chip attendance-chip--absent">Н</span>
            Пропуск
          </button>
          <button
            type="button"
            className="attendance-mini-menu__item"
            onClick={() =>
              void handleDirectStatusSelect(
                contextMenu.row,
                contextMenu.sessionType,
                contextMenu.cycleNumber,
                "EXCUSED",
              )
            }
          >
            <span className="attendance-chip attendance-chip--excused">У</span>
            Уважительная причина
          </button>
          <hr className="attendance-mini-menu__divider" />
          <button
            type="button"
            className="attendance-mini-menu__item"
            onClick={() =>
              handleCommentEdit(
                contextMenu.row,
                contextMenu.sessionType,
                contextMenu.cycleNumber,
              )
            }
          >
            <span>✎</span>
            Примечание / справка...
          </button>
        </div>
      )}

      <Modal
        open={Boolean(commentModal)}
        title={
          commentModal
            ? `Примечание: ${sessionTitle(commentModal.sessionType)} ${commentModal.cycleNumber} — ${commentModal.row.fullName}`
            : "Примечание к посещению"
        }
        onClose={() => setCommentModal(null)}
      >
        {commentModal && (
          <form
            className="admin-form"
            onSubmit={(e) => {
              e.preventDefault();
              void handleSaveCommentModal();
            }}
          >
            <label className="ui-field">
              <span className="ui-field__label">Статус отметки</span>
              <select
                className="ui-input"
                value={commentModal.status}
                onChange={(e) =>
                  setCommentModal((curr) =>
                    curr ? { ...curr, status: e.target.value as AttendanceStatus } : null,
                  )
                }
              >
                <option value="PRESENT">П — Присутствовал</option>
                <option value="ABSENT">Н — Пропуск</option>
                <option value="EXCUSED">У — Уважительная причина</option>
              </select>
            </label>

            <Input
              label="Примечание / номер справки (пустое поле очистит комментарий)"
              placeholder="Например: Справка №124 от 15.11.2026"
              value={commentModal.comment}
              onChange={(e) =>
                setCommentModal((curr) => (curr ? { ...curr, comment: e.target.value } : null))
              }
            />

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem", marginTop: "0.5rem" }}>
              <Button type="button" variant="secondary" onClick={() => setCommentModal(null)}>
                Отмена
              </Button>
              <Button type="submit">
                Сохранить примечание
              </Button>
            </div>
          </form>
        )}
      </Modal>

      <Card>
        <div className="attendance-hint">
          Одиночный клик по ячейке циклически меняет статус: пусто → <strong>П</strong> (присутствовал) →{" "}
          <strong>Н</strong> (пропуск) → <strong>У</strong> (уважительная причина) → пусто. Правый клик (ПКМ)
          открывает быстрое меню выбора статуса и вызова редактора примечания. Двойной клик открывает окно
          редактирования примечания (например, номера справки) без сброса статуса. Процент посещаемости
          рассчитывается как доля присутствий от числа проведённых (отмеченных) занятий.
        </div>
      </Card>
    </div>
  );
};
