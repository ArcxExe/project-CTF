import { useEffect, useState } from "react";
import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { useAuthStore } from "@/features/auth/model/authStore";
import { leaderboardApi, type LeaderboardRow } from "@/shared/api/services/leaderboard";
import { attemptsApi, type AttemptHistoryEntry } from "@/shared/api/services/attempts";
import { gradebookApi, type GradebookRowDto } from "@/shared/api/services/gradebook";
import { attendanceApi } from "@/shared/api/services/attendance";
import type { StudentAttendanceRow, AttendanceStatus } from "@/shared/types/attendance";
import { Loader } from "@/shared/ui/Loader/Loader";
import { useToastStore } from "@/entities/notification/model/toastStore";
import "./pages.css";

const ATTENDANCE_LABELS: Record<AttendanceStatus, { text: string; color: string }> = {
  PRESENT: { text: "П", color: "#4ade80" },
  ABSENT: { text: "Н", color: "#f87171" },
  EXCUSED: { text: "У", color: "#facc15" },
};

export const ParticipantProfilePage = () => {
  const { currentUser } = useAuthStore();
  const { push } = useToastStore();
  const [history, setHistory] = useState<AttemptHistoryEntry[]>([]);
  const [leaderboard, setLeaderboard] = useState<LeaderboardRow[]>([]);
  const [myGradebook, setMyGradebook] = useState<GradebookRowDto | null>(null);
  const [myAttendance, setMyAttendance] = useState<StudentAttendanceRow | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void Promise.all([
      attemptsApi.getHistory().catch(() => [] as AttemptHistoryEntry[]),
      leaderboardApi.getAll().catch(() => [] as LeaderboardRow[]),
      gradebookApi.getMyGradebookRow(),
      attendanceApi.getMyAttendance(),
    ])
      .then(([hist, lb, gb, att]) => {
        setHistory(hist);
        setLeaderboard(lb);
        setMyGradebook(gb);
        setMyAttendance(att);
      })
      .catch(() => {
        push({
          title: "Ошибка загрузки профиля",
          variant: "error",
        });
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [push]);

  if (isLoading) return <Loader label="Загрузка профиля..." />;

  const myLbEntry = currentUser
    ? leaderboard.find(
        (r) =>
          r.participant === currentUser.username ||
          r.participant === currentUser.email ||
          r.participant === currentUser.fullName ||
          r.participant === currentUser.id
      )
    : undefined;

  const ctfScore = myGradebook?.ctfScore ?? myLbEntry?.score ?? 0;
  const totalLab = myGradebook?.totalLab ?? 0;
  const lPercent = myGradebook?.lPercent ?? myLbEntry?.lPercent ?? 0;
  const totalKt = myGradebook?.totalKt ?? 0;
  const kPercent = myGradebook?.kPercent ?? myLbEntry?.kPercent ?? 0;
  const cPercent = myGradebook?.cPercent ?? myLbEntry?.cPercent ?? 0;
  const ctfCoeff = myGradebook?.ctfCoeff ?? Math.min(ctfScore / 800, 1);
  const totalPoints = myGradebook?.totalPoints ?? totalLab + totalKt;
  const sScore = myGradebook?.sScore ?? myLbEntry?.sScore ?? 0;
  const isEligible = myGradebook?.isEligible ?? myLbEntry?.isEligible ?? false;
  const acceptedLabs = myGradebook?.acceptedLabsCount ?? (isEligible ? 8 : 0);
  const grade =
    myGradebook?.grade ??
    (isEligible && myLbEntry?.recommendedGrade ? myLbEntry.recommendedGrade : 2);

  return (
    <div className="page-stack">
      <PageHeader
        title="Профиль студента и успеваемость"
        subtitle="Сводка баллов по регламенту курса: Лабораторные (143 б.), Контрольные точки (100 б.), CTF (800 б.), Успеваемость S% и Посещаемость."
      />

      <div className="grid grid-2">
        <Card>
          <div className="page-stack">
            <h3>Личные данные и итоговая оценка</h3>
            <div className="info-grid">
              <span>ФИО / Ник:</span>
              <strong>{myGradebook?.fullName || currentUser?.fullName || currentUser?.email}</strong>
              <span>Учебная группа:</span>
              <strong>{myGradebook?.groupName || myLbEntry?.group || "—"}</strong>
              <span>Пара (подгруппа):</span>
              <strong>{myGradebook?.pairNumber ?? "Индивидуально"}</strong>
              <span>Лабораторные (Общее ЛР):</span>
              <strong>
                {totalLab.toFixed(2)} / 143.00 ({lPercent.toFixed(2)}%, зачтено {acceptedLabs}/8)
              </strong>
              <span>Контрольные точки (Общее КТ):</span>
              <strong>
                {totalKt.toFixed(2)} / 100.00 ({kPercent.toFixed(2)}%)
              </strong>
              <span>Итог баллов (ЛР + КТ):</span>
              <strong>{totalPoints.toFixed(2)} / 243.00</strong>
              <span>Практикум CTF:</span>
              <strong>
                {ctfScore} / 800 (коэфф. {ctfCoeff.toFixed(2)}, {cPercent.toFixed(2)}%)
              </strong>
              <span>Токен:</span>
              <strong>{myGradebook?.token || "—"}</strong>
              <span>Итоговая успеваемость (S%):</span>
              <strong>{sScore.toFixed(2)}%</strong>
              <span>Допуск к аттестации (8/8 ЛР):</span>
              <strong style={{ color: isEligible ? "#4ade80" : "#f87171" }}>
                {isEligible ? "Допущен (8/8 ЛР зачтены)" : `Нет допуска (${acceptedLabs}/8 ЛР)`}
              </strong>
              <span>Оценка по курсу:</span>
              <strong style={{ fontSize: "1.2rem" }}>
                {grade}{" "}
                {!isEligible && (
                  <span className="muted" style={{ fontSize: "0.82rem", fontWeight: 400 }}>
                    (не зачтены все 8 обязательных ЛР)
                  </span>
                )}
              </strong>
            </div>
          </div>
        </Card>

        <Card>
          <div className="page-stack">
            <h3>История сабмитов CTF</h3>
            {history.length > 0 ? (
              <ul className="page-list" style={{ maxHeight: "320px", overflowY: "auto" }}>
                {history.map((item) => (
                  <li
                    key={item.attemptId}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      padding: "0.5rem 0",
                      borderBottom: "1px solid var(--border)",
                    }}
                  >
                    <div>
                      <strong>{item.challengeTitle}</strong>
                      <div className="muted" style={{ fontSize: "0.85em" }}>
                        {new Date(item.submittedAt).toLocaleString("ru-RU")}
                      </div>
                    </div>
                    <div>
                      {item.correct ? (
                        <span style={{ color: "var(--success)" }}>+{item.earnedPoints}</span>
                      ) : (
                        <span style={{ color: "var(--error)" }}>Неверно</span>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">Нет отправленных флагов или решений.</p>
            )}
          </div>
        </Card>
      </div>

      {myGradebook && (
        <Card>
          <h3 style={{ marginTop: 0, marginBottom: "0.75rem" }}>
            Детализация по ведомости (ЛР 1–8 и КТ 1–4)
          </h3>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "center" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid var(--border)" }}>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
                    <th key={`l-${n}`} style={{ padding: "0.5rem" }}>
                      ЛР {n}
                    </th>
                  ))}
                  <th style={{ padding: "0.5rem" }}>Общее ЛР</th>
                  {[1, 2, 3, 4].map((n) => (
                    <th key={`k-${n}`} style={{ padding: "0.5rem" }}>
                      КТ {n}
                    </th>
                  ))}
                  <th style={{ padding: "0.5rem" }}>Общее КТ</th>
                  <th style={{ padding: "0.5rem" }}>Итог баллов</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => {
                    const cell = myGradebook.labs?.[n];
                    return (
                      <td key={`lv-${n}`} style={{ padding: "0.5rem" }}>
                        <div>{(cell?.score ?? 0).toFixed(2)}</div>
                        <small style={{ color: cell?.accepted ? "#4ade80" : "#94a3b8" }}>
                          {cell?.accepted ? "Зачтено" : `из ${cell?.maxScore ?? 0}`}
                        </small>
                      </td>
                    );
                  })}
                  <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                    {myGradebook.totalLab.toFixed(2)}
                  </td>
                  <td style={{ padding: "0.5rem" }}>{myGradebook.kt1.toFixed(2)}</td>
                  <td style={{ padding: "0.5rem" }}>{myGradebook.kt2.toFixed(2)}</td>
                  <td style={{ padding: "0.5rem" }}>{myGradebook.kt3.toFixed(2)}</td>
                  <td style={{ padding: "0.5rem" }}>{myGradebook.kt4.toFixed(2)}</td>
                  <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                    {myGradebook.totalKt.toFixed(2)}
                  </td>
                  <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                    {myGradebook.totalPoints.toFixed(2)}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {myAttendance && (
        <Card>
          <h3 style={{ marginTop: 0, marginBottom: "0.75rem" }}>
            {`Моя посещаемость лекций и практик — ${myAttendance.attendancePercent.toFixed(1)}% (лекций: ${myAttendance.lecturesPresentCount}/8, практик: ${myAttendance.practicesPresentCount}/8)`}
          </h3>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "center" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid var(--border)" }}>
                  <th style={{ padding: "0.5rem", textAlign: "left" }}>Вид занятия</th>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((c) => (
                    <th key={c} style={{ padding: "0.5rem" }}>
                      Занятие {c}
                    </th>
                  ))}
                  <th style={{ padding: "0.5rem" }}>Присутствовал</th>
                </tr>
              </thead>
              <tbody>
                <tr style={{ borderBottom: "1px solid rgba(148,163,184,0.15)" }}>
                  <td style={{ padding: "0.5rem", textAlign: "left", fontWeight: 600 }}>
                    Лекции (1–8)
                  </td>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((c) => {
                    const st = myAttendance.lectures?.[String(c)];
                    const info = st ? ATTENDANCE_LABELS[st] : null;
                    return (
                      <td
                        key={`lec-${c}`}
                        style={{
                          padding: "0.5rem",
                          fontWeight: 700,
                          color: info?.color ?? "inherit",
                        }}
                      >
                        {info ? info.text : "—"}
                      </td>
                    );
                  })}
                  <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                    {myAttendance.lecturesPresentCount} / 8
                  </td>
                </tr>
                <tr>
                  <td style={{ padding: "0.5rem", textAlign: "left", fontWeight: 600 }}>
                    Практики (1–8)
                  </td>
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((c) => {
                    const st = myAttendance.practices?.[String(c)];
                    const info = st ? ATTENDANCE_LABELS[st] : null;
                    return (
                      <td
                        key={`prac-${c}`}
                        style={{
                          padding: "0.5rem",
                          fontWeight: 700,
                          color: info?.color ?? "inherit",
                        }}
                      >
                        {info ? info.text : "—"}
                      </td>
                    );
                  })}
                  <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                    {myAttendance.practicesPresentCount} / 8
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
};
