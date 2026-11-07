import { useState, useEffect, useMemo } from "react";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Card } from "@/shared/ui/Card/Card";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Input } from "@/shared/ui/Input/Input";
import { useToastStore } from "@/entities/notification/model/toastStore";
import {
  analyticsApi,
  AnalyticsSummary,
  TaskAnalytics,
  StudentTestAnalytics,
  StudentChallengeAnalytics,
} from "@/shared/api/services/analytics";
import { gradebookApi, type GradebookRowDto } from "@/shared/api/services/gradebook";
import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";

export const AdminAnalyticsPage = () => {
  const { push } = useToastStore();
  const [flows, setFlows] = useState<any[]>([]);
  const [groups, setGroups] = useState<any[]>([]);

  const [selectedFlowId, setSelectedFlowId] = useState<string>("");
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");

  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [gradebookRows, setGradebookRows] = useState<GradebookRowDto[]>([]);
  const [tasks, setTasks] = useState<TaskAnalytics[]>([]);
  const [studentTests, setStudentTests] = useState<StudentTestAnalytics[]>([]);
  const [studentChallenges, setStudentChallenges] = useState<StudentChallengeAnalytics[]>([]);

  const [selectedStudentId, setSelectedStudentId] = useState<string>("");
  const [activeSubTab, setActiveSubTab] = useState<"gradebook" | "tests" | "challenges">("gradebook");

  const [testSearch, setTestSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void loadFilters();
  }, []);

  useEffect(() => {
    void loadData();
  }, [selectedFlowId, selectedGroupId]);

  const loadFilters = async () => {
    try {
      const [f, g] = await Promise.all([streamsApi.getAll(), groupsApi.getAll()]);
      setFlows(f);
      setGroups(g);
    } catch {
      push({ title: "Ошибка загрузки фильтров", variant: "error" });
    }
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [aData, tData, stData, scData, gbData] = await Promise.all([
        analyticsApi.getGroupAnalytics(selectedGroupId || undefined, selectedFlowId || undefined),
        analyticsApi.getTaskAnalytics(selectedGroupId || undefined, selectedFlowId || undefined),
        analyticsApi.getStudentTestAnalytics(selectedGroupId || undefined, selectedFlowId || undefined),
        analyticsApi.getStudentChallengeAnalytics(selectedGroupId || undefined, selectedFlowId || undefined),
        gradebookApi.getGradebook(selectedGroupId || undefined, selectedFlowId || undefined).catch(() => [] as GradebookRowDto[]),
      ]);
      setAnalytics(aData);
      setTasks(tData);
      setStudentTests(stData);
      setStudentChallenges(scData);
      setGradebookRows(gbData);

      if (aData.students.length > 0) {
        setSelectedStudentId((prev) => {
          const exists = aData.students.some((s) => s.id === prev);
          return exists ? prev : aData.students[0].id;
        });
      } else {
        setSelectedStudentId("");
      }
    } catch {
      push({ title: "Ошибка загрузки аналитики", variant: "error" });
    } finally {
      setIsLoading(false);
    }
  };

  const filteredStudentTests = useMemo(() => {
    return studentTests.filter((st) => {
      const query = testSearch.trim().toLowerCase();
      if (!query) return true;
      return (
        st.studentName.toLowerCase().includes(query) ||
        st.testTitle.toLowerCase().includes(query) ||
        st.groupName.toLowerCase().includes(query)
      );
    });
  }, [studentTests, testSearch]);

  const selectedStudent = useMemo(() => {
    return analytics?.students.find((s) => s.id === selectedStudentId) || null;
  }, [analytics?.students, selectedStudentId]);

  const selectedStudentGradebook = useMemo(() => {
    return gradebookRows.find((r) => r.studentId === selectedStudentId) || null;
  }, [gradebookRows, selectedStudentId]);

  const selectedStudentTests = useMemo(() => {
    return studentTests.filter((t) => t.studentId === selectedStudentId);
  }, [studentTests, selectedStudentId]);

  const selectedStudentChallenges = useMemo(() => {
    return studentChallenges.filter((c) => c.studentId === selectedStudentId);
  }, [studentChallenges, selectedStudentId]);

  const escapeCSV = (str: string | number | null | undefined) =>
    `"${String(str ?? "").replace(/"/g, '""')}"`;

  const handleExportCsv = () => {
    if (!analytics) return;

    const headers = [
      "Пара",
      "ФИО студента",
      "Группа",
      "Статус",
      "Общее ЛР (из 143)",
      "Допуск ЛР (из 8)",
      "L%",
      "Общее КТ (из 100)",
      "K%",
      "Итог баллов (из 243)",
      "Балл CTF (из 800)",
      "Коэфф. CTF",
      "C%",
      "Токен",
      "Посещаемость %",
      "Успеваемость (S%)",
      "Оценка",
    ];
    const rows = analytics.students.map((s) => [
      s.pairNumber ?? "",
      escapeCSV(s.fio || s.fullName),
      escapeCSV(s.groupName || ""),
      escapeCSV(s.status),
      (s.exactLabScore ?? s.labScore ?? 0).toFixed(2),
      `${s.acceptedLabsCount ?? 0}/8`,
      (s.lPercent ?? s.v1 ?? 0).toFixed(2),
      (s.ktScore ?? 0).toFixed(2),
      (s.kPercent ?? 0).toFixed(2),
      (s.totalPoints ?? s.labScore ?? 0).toFixed(2),
      s.ctfScore,
      (s.ctfCoeff ?? Math.min(s.ctfScore / 800, 1)).toFixed(2),
      (s.cPercent ?? s.v2 ?? 0).toFixed(2),
      escapeCSV(s.token || ""),
      (s.attendancePercent ?? 0).toFixed(1),
      (s.sScore ?? s.totalScore100 ?? 0).toFixed(2),
      s.numericGrade ?? (s.recommendedGrade !== "Н/Д" ? s.recommendedGrade : 2),
    ]);

    const csvContent =
      "\uFEFF" + [headers.join(","), ...rows.map((e) => e.join(","))].join("\n");

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", "Аналитика_успеваемости.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleExportGradebookXlsx = async () => {
    try {
      await gradebookApi.exportXlsx(selectedGroupId || undefined, selectedFlowId || undefined);
      push({ title: "Ведомость выгружена в Excel (.xlsx)", variant: "success" });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка выгрузки XLSX",
        variant: "error",
      });
    }
  };

  return (
    <div className="page-stack">
      <PageHeader
        title="Аналитика с учётом лабораторных и успеваемости"
        subtitle="Комплексная аналитика по регламенту курса: лабораторные работы (143 б., допуск 8/8), контрольные точки (100 б.), практикум CTF (800 б.), посещаемость и итоговые оценки."
        actions={
          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
            <Button variant="secondary" onClick={handleExportCsv}>
              Экспорт аналитики (CSV)
            </Button>
            <Button variant="secondary" onClick={() => void handleExportGradebookXlsx()}>
              Экспорт Ведомости (.xlsx)
            </Button>
            <Button onClick={() => void loadData()}>Обновить</Button>
          </div>
        }
      />

      <Card>
        <div className="page-stack" style={{ display: "flex", gap: "1rem", alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: 500 }}>Поток</label>
            <select
              value={selectedFlowId}
              onChange={(e) => {
                setSelectedFlowId(e.target.value);
                setSelectedGroupId("");
              }}
              style={{ padding: "0.5rem", borderRadius: "4px", border: "1px solid #ccc" }}
            >
              <option value="">Все потоки</option>
              {flows.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: 500 }}>Группа</label>
            <select
              value={selectedGroupId}
              onChange={(e) => setSelectedGroupId(e.target.value)}
              style={{ padding: "0.5rem", borderRadius: "4px", border: "1px solid #ccc" }}
            >
              <option value="">Все группы</option>
              {groups
                .filter((g) => !selectedFlowId || g.streamId === selectedFlowId)
                .map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
            </select>
          </div>
        </div>
      </Card>

      {isLoading || !analytics ? (
        <Loader label="Загрузка аналитики..." />
      ) : (
        <>
          <div className="metric-grid">
            <Card>
              <div className="metric-card">
                <span className="muted">Лабораторные (ЛР 1–8)</span>
                <strong>{analytics.metrics.avgLabScore.toFixed(2)} / 143</strong>
                <Badge tone="info">
                  Ср. L%: {(analytics.metrics.avgLabPercent ?? 0).toFixed(1)}% | Макс: {analytics.metrics.maxLabScore}
                </Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Допуск по ЛР (8 из 8)</span>
                <strong>{(analytics.metrics.labAdmissionPercentage ?? 0).toFixed(1)}%</strong>
                <Badge tone={(analytics.metrics.labAdmissionPercentage ?? 0) >= 70 ? "success" : "info"}>
                  Активных: {analytics.metrics.admissionPercentage.toFixed(0)}% ({analytics.students.length} студ.)
                </Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Контрольные точки (КТ 1–4)</span>
                <strong>{(analytics.metrics.avgKtScore ?? 0).toFixed(2)} / 100</strong>
                <Badge tone="info">Ср. K%: {(analytics.metrics.avgKtPercent ?? 0).toFixed(1)}%</Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Практикум CTF (норма 800)</span>
                <strong>{analytics.metrics.avgCtfScore.toFixed(1)} / 800</strong>
                <Badge tone="info">
                  Ср. C%: {(analytics.metrics.avgCtfPercent ?? 0).toFixed(1)}% | Макс: {analytics.metrics.maxCtfScore}
                </Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Ср. Успеваемость (S%)</span>
                <strong>{(analytics.metrics.avgSScore ?? 0).toFixed(2)}%</strong>
                <Badge tone="success">
                  Посещаемость: {(analytics.metrics.avgAttendancePercent ?? 0).toFixed(1)}%
                </Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Распределение оценок</span>
                <strong>
                  5: {analytics.metrics.grade5Count ?? 0} | 4: {analytics.metrics.grade4Count ?? 0} | 3:{" "}
                  {analytics.metrics.grade3Count ?? 0} | 2: {analytics.metrics.grade2Count ?? 0}
                </strong>
                <Badge tone={analytics.metrics.blockedOrDisqualifiedCount > 0 ? "danger" : "neutral"}>
                  Вне рейтинга / блок: {analytics.metrics.blockedOrDisqualifiedCount}
                </Badge>
              </div>
            </Card>
          </div>

          <Card>
            <div
              className="page-stack"
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "0.75rem" }}
            >
              <div>
                <h3 style={{ margin: 0 }}>Сводная ведомость успеваемости студентов (по регламенту курса)</h3>
                <p className="muted" style={{ margin: "0.25rem 0 0 0", fontSize: "0.85rem" }}>
                  Нажмите на ФИО студента для просмотра детальной разбивки ЛР 1–8, КТ 1–4, тестов и задач CTF.
                </p>
              </div>
              <Button onClick={handleExportCsv}>Экспорт CSV</Button>
            </div>
            <DataTable
              columns={[
                {
                  key: "pairNumber",
                  title: "Пара",
                  render: (r: any) => r.pairNumber ?? "—",
                },
                {
                  key: "fio",
                  title: "ФИО студента",
                  render: (r: any) => (
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedStudentId(r.id);
                        document
                          .getElementById("detailed-student-analytics")
                          ?.scrollIntoView({ behavior: "smooth" });
                      }}
                      style={{
                        background: "none",
                        border: "none",
                        color: "var(--primary)",
                        textDecoration: "underline",
                        cursor: "pointer",
                        padding: 0,
                        font: "inherit",
                        textAlign: "left",
                        fontWeight: 600,
                      }}
                    >
                      {r.fio || r.fullName}
                    </button>
                  ),
                },
                { key: "groupName", title: "Группа", render: (r: any) => r.groupName || "—" },
                {
                  key: "exactLabScore",
                  title: "Общее ЛР (143)",
                  render: (r: any) => (
                    <div>
                      <strong>{(r.exactLabScore ?? r.labScore ?? 0).toFixed(2)}</strong>
                      <div className="muted" style={{ fontSize: "0.75rem" }}>
                        L: {(r.lPercent ?? r.v1 ?? 0).toFixed(1)}% ({r.acceptedLabsCount ?? 0}/8 зачт.)
                      </div>
                    </div>
                  ),
                },
                {
                  key: "ktScore",
                  title: "Общее КТ (100)",
                  render: (r: any) => (
                    <div>
                      <strong>{(r.ktScore ?? 0).toFixed(2)}</strong>
                      <div className="muted" style={{ fontSize: "0.75rem" }}>
                        K: {(r.kPercent ?? 0).toFixed(1)}%
                      </div>
                    </div>
                  ),
                },
                {
                  key: "totalPoints",
                  title: "Итог баллов (243)",
                  render: (r: any) => (
                    <strong>{(r.totalPoints ?? r.labScore ?? 0).toFixed(2)}</strong>
                  ),
                },
                {
                  key: "ctfScore",
                  title: "CTF",
                  render: (r: any) => (
                    <div>
                      <strong>{(r.ctfCoeff ?? Math.min(r.ctfScore / 800, 1)).toFixed(2)}</strong>
                      <div className="muted" style={{ fontSize: "0.75rem" }}>
                        {r.ctfScore} б. ({(r.cPercent ?? r.v2 ?? 0).toFixed(1)}%)
                      </div>
                    </div>
                  ),
                },
                {
                  key: "token",
                  title: "Токен",
                  render: (r: any) => r.token || "—",
                },
                {
                  key: "attendancePercent",
                  title: "Посещ.",
                  render: (r: any) => `${(r.attendancePercent ?? 0).toFixed(1)}%`,
                },
                {
                  key: "sScore",
                  title: "Успеваемость (S%)",
                  render: (r: any) => (
                    <strong>{(r.sScore ?? r.totalScore100 ?? 0).toFixed(2)}</strong>
                  ),
                },
                {
                  key: "numericGrade",
                  title: "Оценка",
                  render: (r: any) => {
                    const g = r.numericGrade ?? (r.recommendedGrade !== "Н/Д" ? Number(r.recommendedGrade) : 2);
                    const tone = g === 5 ? "success" : g === 4 ? "info" : g === 3 ? "neutral" : "danger";
                    return (
                      <Badge tone={tone}>
                        {g} {!r.isEligible ? "(недопуск)" : ""}
                      </Badge>
                    );
                  },
                },
              ]}
              rows={analytics.students}
            />
          </Card>

          <Card>
            <div className="page-stack" id="detailed-student-analytics">
              <h3>Индивидуальная карточка успеваемости студента</h3>

              <div
                style={{
                  display: "flex",
                  gap: "1rem",
                  alignItems: "center",
                  flexWrap: "wrap",
                  marginBottom: "1rem",
                }}
              >
                <div style={{ flex: 1, minWidth: "250px" }}>
                  <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: 500 }}>
                    Выберите студента для просмотра детальной истории
                  </label>
                  <select
                    value={selectedStudentId}
                    onChange={(e) => setSelectedStudentId(e.target.value)}
                    style={{
                      width: "100%",
                      padding: "0.5rem",
                      borderRadius: "4px",
                      border: "1px solid var(--border)",
                      background: "var(--bg)",
                      color: "var(--text)",
                      font: "inherit",
                    }}
                  >
                    <option value="">-- Выберите студента --</option>
                    {analytics.students.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.fio || s.fullName} ({s.groupName || "Без группы"})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {selectedStudent ? (
                <div
                  className="page-stack"
                  style={{
                    borderTop: "1px solid var(--border)",
                    paddingTop: "1.5rem",
                    marginTop: "1rem",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      flexWrap: "wrap",
                      gap: "1rem",
                    }}
                  >
                    <div>
                      <h4 style={{ margin: 0, fontSize: "1.25rem" }}>
                        {selectedStudent.fio || selectedStudent.fullName}
                      </h4>
                      <div
                        style={{
                          color: "var(--text-secondary)",
                          fontSize: "0.9rem",
                          marginTop: "0.25rem",
                        }}
                      >
                        Оценка по ведомости:{" "}
                        <Badge tone={(selectedStudent.numericGrade ?? 2) >= 4 ? "success" : "danger"}>
                          {selectedStudent.numericGrade ?? 2}
                        </Badge>{" "}
                        • Допуск ЛР: {selectedStudent.acceptedLabsCount ?? 0}/8{" "}
                        {selectedStudent.isEligible ? "(допущен)" : "(не допущен)"}
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: "1.5rem", flexWrap: "wrap" }}>
                      <div style={{ textAlign: "right" }}>
                        <span className="muted" style={{ display: "block", fontSize: "0.8rem" }}>
                          Общее ЛР (143)
                        </span>
                        <strong style={{ fontSize: "1.2rem" }}>
                          {(selectedStudent.exactLabScore ?? selectedStudent.labScore).toFixed(2)}
                        </strong>
                      </div>
                      <div style={{ textAlign: "right" }}>
                        <span className="muted" style={{ display: "block", fontSize: "0.8rem" }}>
                          Общее КТ (100)
                        </span>
                        <strong style={{ fontSize: "1.2rem" }}>
                          {(selectedStudent.ktScore ?? 0).toFixed(2)}
                        </strong>
                      </div>
                      <div style={{ textAlign: "right" }}>
                        <span className="muted" style={{ display: "block", fontSize: "0.8rem" }}>
                          Итог баллов (243)
                        </span>
                        <strong style={{ fontSize: "1.2rem" }}>
                          {(selectedStudent.totalPoints ?? 0).toFixed(2)}
                        </strong>
                      </div>
                      <div style={{ textAlign: "right" }}>
                        <span className="muted" style={{ display: "block", fontSize: "0.8rem" }}>
                          CTF (800)
                        </span>
                        <strong style={{ fontSize: "1.2rem" }}>{selectedStudent.ctfScore}</strong>
                      </div>
                      <div style={{ textAlign: "right" }}>
                        <span className="muted" style={{ display: "block", fontSize: "0.8rem" }}>
                          Успеваемость S%
                        </span>
                        <strong style={{ fontSize: "1.2rem" }}>
                          {(selectedStudent.sScore ?? selectedStudent.totalScore100).toFixed(2)}%
                        </strong>
                      </div>
                    </div>
                  </div>

                  <div
                    style={{
                      display: "flex",
                      gap: "0.5rem",
                      marginTop: "1.5rem",
                      borderBottom: "1px solid var(--border)",
                      paddingBottom: "0.5rem",
                      flexWrap: "wrap",
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => setActiveSubTab("gradebook")}
                      style={{
                        padding: "0.5rem 1rem",
                        border: "none",
                        background: activeSubTab === "gradebook" ? "var(--primary)" : "transparent",
                        color: activeSubTab === "gradebook" ? "white" : "var(--text)",
                        borderRadius: "4px",
                        cursor: "pointer",
                        fontWeight: 600,
                      }}
                    >
                      Лабораторные ЛР 1–8 и КТ 1–4 (Ведомость)
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveSubTab("tests")}
                      style={{
                        padding: "0.5rem 1rem",
                        border: "none",
                        background: activeSubTab === "tests" ? "var(--primary)" : "transparent",
                        color: activeSubTab === "tests" ? "white" : "var(--text)",
                        borderRadius: "4px",
                        cursor: "pointer",
                        fontWeight: 600,
                      }}
                    >
                      Тесты ({selectedStudentTests.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveSubTab("challenges")}
                      style={{
                        padding: "0.5rem 1rem",
                        border: "none",
                        background: activeSubTab === "challenges" ? "var(--primary)" : "transparent",
                        color: activeSubTab === "challenges" ? "white" : "var(--text)",
                        borderRadius: "4px",
                        cursor: "pointer",
                        fontWeight: 600,
                      }}
                    >
                      Задания CTF ({selectedStudentChallenges.length})
                    </button>
                  </div>

                  <div style={{ marginTop: "1rem" }}>
                    {activeSubTab === "gradebook" ? (
                      selectedStudentGradebook ? (
                        <div style={{ overflowX: "auto" }}>
                          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "center" }}>
                            <thead>
                              <tr style={{ borderBottom: "1px solid var(--border)" }}>
                                {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
                                  <th key={n} style={{ padding: "0.5rem" }}>
                                    ЛР {n}
                                  </th>
                                ))}
                                <th style={{ padding: "0.5rem" }}>Общее ЛР</th>
                                {[1, 2, 3, 4].map((n) => (
                                  <th key={n} style={{ padding: "0.5rem" }}>
                                    КТ {n}
                                  </th>
                                ))}
                                <th style={{ padding: "0.5rem" }}>Общее КТ</th>
                                <th style={{ padding: "0.5rem" }}>Итог баллов</th>
                                <th style={{ padding: "0.5rem" }}>CTF</th>
                                <th style={{ padding: "0.5rem" }}>Токен</th>
                                <th style={{ padding: "0.5rem" }}>S%</th>
                                <th style={{ padding: "0.5rem" }}>Оценка</th>
                              </tr>
                            </thead>
                            <tbody>
                              <tr>
                                {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => {
                                  const cell = selectedStudentGradebook.labs?.[n];
                                  return (
                                    <td key={n} style={{ padding: "0.5rem" }}>
                                      <strong>{(cell?.score ?? 0).toFixed(2)}</strong>
                                      <div
                                        style={{
                                          fontSize: "0.72rem",
                                          color: cell?.accepted ? "#4ade80" : "#f87171",
                                        }}
                                      >
                                        {cell?.accepted ? "Зачтено" : `из ${cell?.maxScore ?? 0}`}
                                      </div>
                                    </td>
                                  );
                                })}
                                <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                                  {selectedStudentGradebook.totalLab.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.kt1.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.kt2.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.kt3.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.kt4.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                                  {selectedStudentGradebook.totalKt.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                                  {selectedStudentGradebook.totalPoints.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.ctfCoeff.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem" }}>
                                  {selectedStudentGradebook.token || "—"}
                                </td>
                                <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                                  {selectedStudentGradebook.sScore.toFixed(2)}
                                </td>
                                <td style={{ padding: "0.5rem", fontWeight: 700 }}>
                                  {selectedStudentGradebook.grade}
                                </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <p className="muted">Данные ведомости для выбранного студента отсутствуют.</p>
                      )
                    ) : activeSubTab === "tests" ? (
                      <DataTable
                        columns={[
                          { key: "testTitle", title: "Тест" },
                          {
                            key: "status",
                            title: "Статус",
                            render: (r: any) => {
                              let tone: "success" | "danger" | "info" | "neutral" = "neutral";
                              let label = "Не приступал";
                              if (r.status === "COMPLETED") {
                                tone = "success";
                                label = "Решен";
                              } else if (r.status === "IN_PROGRESS") {
                                tone = "info";
                                label = "В процессе";
                              }
                              return <Badge tone={tone}>{label}</Badge>;
                            },
                          },
                          {
                            key: "score",
                            title: "Баллы / Порог",
                            render: (r: any) => {
                              if (r.status === "NOT_STARTED") return "—";
                              const isPassed = r.score >= r.passingScore;
                              return (
                                <span
                                  style={{
                                    color: isPassed ? "var(--success)" : "var(--danger)",
                                    fontWeight: "bold",
                                  }}
                                >
                                  {r.score} (порог: {r.passingScore})
                                </span>
                              );
                            },
                          },
                          {
                            key: "date",
                            title: "Время сдачи/активности",
                            render: (r: any) =>
                              r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "—",
                          },
                        ]}
                        rows={selectedStudentTests}
                      />
                    ) : (
                      <DataTable
                        columns={[
                          { key: "challengeName", title: "Задание" },
                          { key: "category", title: "Категория" },
                          { key: "maxScore", title: "Макс. балл" },
                          {
                            key: "solved",
                            title: "Статус",
                            render: (r: any) => (
                              <Badge
                                tone={
                                  r.solved
                                    ? "success"
                                    : r.attemptsCount > 0
                                    ? "info"
                                    : "neutral"
                                }
                              >
                                {r.solved
                                  ? "Решено"
                                  : r.attemptsCount > 0
                                  ? "Есть попытки"
                                  : "Не приступал"}
                              </Badge>
                            ),
                          },
                          {
                            key: "scoreAwarded",
                            title: "Получено баллов",
                            render: (r: any) => (r.solved ? r.scoreAwarded : "—"),
                          },
                          { key: "attemptsCount", title: "Всего попыток" },
                          {
                            key: "date",
                            title: "Время последнего действия",
                            render: (r: any) =>
                              r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "—",
                          },
                        ]}
                        rows={selectedStudentChallenges}
                      />
                    )}
                  </div>
                </div>
              ) : (
                <div
                  style={{
                    color: "var(--text-secondary)",
                    padding: "2rem 0",
                    textAlign: "center",
                  }}
                >
                  Студент не выбран или список студентов пуст.
                </div>
              )}
            </div>
          </Card>

          <Card>
            <div className="page-stack">
              <h3>Игровая аналитика (По задачам CTF)</h3>
              <DataTable
                columns={[
                  { key: "challengeName", title: "Задание" },
                  {
                    key: "solvePercentage",
                    title: "% Решаемости",
                    render: (r: any) => `${r.solvePercentage.toFixed(1)}%`,
                  },
                  { key: "firstBloodStudentName", title: "First Blood (Студент)" },
                  {
                    key: "firstBloodTime",
                    title: "Время FB",
                    render: (r: any) =>
                      r.firstBloodTime !== "-"
                        ? new Date(r.firstBloodTime).toLocaleString("ru")
                        : "-",
                  },
                  { key: "incorrectAttempts", title: "Неверных попыток" },
                ]}
                rows={tasks}
              />
            </div>
          </Card>

          <Card>
            <div className="page-stack">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3>Прохождение тестов и КТ студентами</h3>
                <Input
                  placeholder="Поиск по студенту или тесту..."
                  value={testSearch}
                  onChange={(e) => setTestSearch(e.target.value)}
                  style={{ maxWidth: "300px", margin: 0 }}
                />
              </div>
              <DataTable
                columns={[
                  { key: "studentName", title: "Студент" },
                  { key: "groupName", title: "Группа" },
                  { key: "testTitle", title: "Тест / КТ" },
                  {
                    key: "status",
                    title: "Статус",
                    render: (r: any) => {
                      let tone: "success" | "danger" | "info" | "neutral" = "neutral";
                      let label = "Не приступал";
                      if (r.status === "COMPLETED") {
                        tone = "success";
                        label = "Решен";
                      } else if (r.status === "IN_PROGRESS") {
                        tone = "info";
                        label = "В процессе";
                      }
                      return <Badge tone={tone}>{label}</Badge>;
                    },
                  },
                  {
                    key: "score",
                    title: "Баллы (Проходной)",
                    render: (r: any) => {
                      if (r.status === "NOT_STARTED") return "-";
                      const isPassed = r.score >= r.passingScore;
                      return (
                        <span
                          style={{
                            color: isPassed ? "var(--success)" : "var(--danger)",
                            fontWeight: "bold",
                          }}
                        >
                          {r.score} (порог: {r.passingScore})
                        </span>
                      );
                    },
                  },
                  {
                    key: "date",
                    title: "Время сдачи",
                    render: (r: any) =>
                      r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "-",
                  },
                ]}
                rows={filteredStudentTests}
              />
            </div>
          </Card>
        </>
      )}
    </div>
  );
};
