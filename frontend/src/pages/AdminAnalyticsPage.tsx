import { useState, useEffect, useMemo } from "react";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Card } from "@/shared/ui/Card/Card";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Input } from "@/shared/ui/Input/Input";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { analyticsApi, AnalyticsSummary, TaskAnalytics, StudentTestAnalytics, StudentChallengeAnalytics } from "@/shared/api/services/analytics";
import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";

export const AdminAnalyticsPage = () => {
  const { push } = useToastStore();
  const [flows, setFlows] = useState<any[]>([]);
  const [groups, setGroups] = useState<any[]>([]);

  const [selectedFlowId, setSelectedFlowId] = useState<string>("");
  const [selectedGroupId, setSelectedGroupId] = useState<string>("");

  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [tasks, setTasks] = useState<TaskAnalytics[]>([]);
  const [studentTests, setStudentTests] = useState<StudentTestAnalytics[]>([]);
  const [studentChallenges, setStudentChallenges] = useState<StudentChallengeAnalytics[]>([]);
  
  const [selectedStudentId, setSelectedStudentId] = useState<string>("");
  const [activeSubTab, setActiveSubTab] = useState<"tests" | "challenges">("tests");

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
      const [f, g] = await Promise.all([
        streamsApi.getAll(),
        groupsApi.getAll()
      ]);
      setFlows(f);
      setGroups(g);
    } catch (e) {
      push({ title: "Ошибка загрузки фильтров", variant: "error" });
    }
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [aData, tData, stData, scData] = await Promise.all([
        analyticsApi.getGroupAnalytics(selectedGroupId, selectedFlowId),
        analyticsApi.getTaskAnalytics(selectedGroupId, selectedFlowId),
        analyticsApi.getStudentTestAnalytics(selectedGroupId, selectedFlowId),
        analyticsApi.getStudentChallengeAnalytics(selectedGroupId, selectedFlowId)
      ]);
      setAnalytics(aData);
      setTasks(tData);
      setStudentTests(stData);
      setStudentChallenges(scData);

      if (aData.students.length > 0) {
        setSelectedStudentId((prev) => {
          const exists = aData.students.some(s => s.id === prev);
          return exists ? prev : aData.students[0].id;
        });
      } else {
        setSelectedStudentId("");
      }
    } catch (error) {
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

  const avgTestScore = useMemo(() => {
    const completedTests = studentTests.filter(t => t.status === "COMPLETED");
    if (completedTests.length === 0) return 0;
    const sum = completedTests.reduce((acc, t) => acc + t.score, 0);
    return sum / completedTests.length;
  }, [studentTests]);

  const selectedStudent = useMemo(() => {
    return analytics?.students.find(s => s.id === selectedStudentId) || null;
  }, [analytics?.students, selectedStudentId]);

  const selectedStudentTests = useMemo(() => {
    return studentTests.filter(t => t.studentId === selectedStudentId);
  }, [studentTests, selectedStudentId]);

  const selectedStudentChallenges = useMemo(() => {
    return studentChallenges.filter(c => c.studentId === selectedStudentId);
  }, [studentChallenges, selectedStudentId]);

  const escapeCSV = (str: string | number) => `"${String(str).replace(/"/g, '""')}"`;

  const handleExportCsv = () => {
    if (!analytics) return;

    const headers = ["ФИО студента", "Статус", "Балл Лабы (M)", "Балл CTF (N)", "v1", "v2", "Итоговый балл (S)", "Оценка"];
    const rows = analytics.students.map(s => [
      escapeCSV(s.fullName),
      escapeCSV(s.status),
      s.labScore,
      s.ctfScore,
      s.v1.toFixed(2),
      s.v2.toFixed(2),
      s.totalScore100,
      escapeCSV(s.recommendedGrade)
    ]);

    const csvContent = [
      headers.join(","),
      ...rows.map(e => e.join(","))
    ].join("\n");

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", "analytics_export.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="page-stack">
      <PageHeader
        title="Аналитика"
        subtitle="Сводка активности, прогресса потоков и качества заданий."
        actions={<Button onClick={loadData}>Обновить</Button>}
      />

      <Card>
        <div className="page-stack" style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div>
            <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>Поток</label>
            <select
              value={selectedFlowId}
              onChange={(e) => {
                setSelectedFlowId(e.target.value);
                setSelectedGroupId("");
              }}
              style={{ padding: '0.5rem', borderRadius: '4px', border: '1px solid #ccc' }}
            >
              <option value="">Все потоки</option>
              {flows.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
            </select>
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>Группа</label>
            <select
              value={selectedGroupId}
              onChange={(e) => setSelectedGroupId(e.target.value)}
              style={{ padding: '0.5rem', borderRadius: '4px', border: '1px solid #ccc' }}
            >
              <option value="">Все группы</option>
              {groups
                .filter(g => !selectedFlowId || g.streamId === selectedFlowId)
                .map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
          </div>
        </div>
      </Card>

      {isLoading || !analytics ? <Loader label="Загрузка аналитики..." /> : (
        <>
          <div className="metric-grid">
            <Card>
              <div className="metric-card">
                <span className="muted">M_max (Лабы)</span>
                <strong>{analytics.metrics.maxLabScore}</strong>
                <Badge tone="info">Средний: {analytics.metrics.avgLabScore.toFixed(1)}</Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">N_max (CTF)</span>
                <strong>{analytics.metrics.maxCtfScore}</strong>
                <Badge tone="info">Средний: {analytics.metrics.avgCtfScore.toFixed(1)}</Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Всего студентов</span>
                <strong>{analytics.students.length}</strong>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Допущены</span>
                <strong>{analytics.metrics.admissionPercentage.toFixed(1)}%</strong>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Заблокированы</span>
                <strong>{analytics.metrics.blockedOrDisqualifiedCount}</strong>
                <Badge tone="danger">Внимание</Badge>
              </div>
            </Card>
            <Card>
              <div className="metric-card">
                <span className="muted">Ср. успеваемость (Тесты)</span>
                <strong>{avgTestScore.toFixed(1)}%</strong>
                <Badge tone="success">
                  Решено: {studentTests.filter(t => t.status === "COMPLETED").length}
                </Badge>
              </div>
            </Card>
          </div>

          <Card>
            <div className="page-stack">
              <h3>Игровая аналитика (По задачам)</h3>
              <DataTable
                columns={[
                  { key: "challengeName", title: "Задание" },
                  { key: "solvePercentage", title: "% Решаемости", render: (r: any) => `${r.solvePercentage.toFixed(1)}%` },
                  { key: "firstBloodStudentName", title: "First Blood (Студент)" },
                  { key: "firstBloodTime", title: "Время FB", render: (r: any) => r.firstBloodTime !== "-" ? new Date(r.firstBloodTime).toLocaleString("ru") : "-" },
                  { key: "incorrectAttempts", title: "Неверных попыток" }
                ]}
                rows={tasks}
              />
            </div>
          </Card>

          <Card>
            <div className="page-stack">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3>Прохождение тестов студентами</h3>
                <Input
                  placeholder="Поиск по студенту или тесту..."
                  value={testSearch}
                  onChange={(e) => setTestSearch(e.target.value)}
                  style={{ maxWidth: '300px', margin: 0 }}
                />
              </div>
              <DataTable
                columns={[
                  { key: "studentName", title: "Студент" },
                  { key: "groupName", title: "Группа" },
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
                    }
                  },
                  { 
                    key: "score", 
                    title: "Баллы (Проходной)",
                    render: (r: any) => {
                      if (r.status === "NOT_STARTED") return "-";
                      const isPassed = r.score >= r.passingScore;
                      return (
                        <span style={{ color: isPassed ? "var(--success)" : "var(--danger)", fontWeight: 'bold' }}>
                          {r.score}% (порог: {r.passingScore}%)
                        </span>
                      );
                    }
                  },
                  { 
                    key: "date", 
                    title: "Время сдачи",
                    render: (r: any) => r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "-"
                  }
                ]}
                rows={filteredStudentTests}
              />
            </div>
          </Card>

          <Card>
            <div className="page-stack" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3>Итоговая таблица студентов</h3>
              <Button onClick={handleExportCsv}>Экспорт CSV</Button>
            </div>
            <DataTable
              columns={[
                {
                  key: "fullName",
                  title: "ФИО",
                  render: (r: any) => (
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedStudentId(r.id);
                        document.getElementById("detailed-student-analytics")?.scrollIntoView({ behavior: 'smooth' });
                      }}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: 'var(--primary)',
                        textDecoration: 'underline',
                        cursor: 'pointer',
                        padding: 0,
                        font: 'inherit',
                        textAlign: 'left'
                      }}
                    >
                      {r.fullName}
                    </button>
                  )
                },
                { key: "status", title: "Статус", render: (r: any) => <Badge tone={r.status === 'ACTIVE' ? 'success' : 'danger'}>{r.status}</Badge> },
                { key: "labScore", title: "M (Лабы)" },
                { key: "ctfScore", title: "N (CTF)" },
                { key: "v1", title: "v1", render: (r: any) => r.v1.toFixed(2) },
                { key: "v2", title: "v2", render: (r: any) => r.v2.toFixed(2) },
                { key: "totalScore100", title: "Итого (S*100)" },
                { key: "recommendedGrade", title: "Оценка", render: (r: any) => <Badge tone="info">{r.recommendedGrade}</Badge> },
              ]}
              rows={analytics.students}
            />
          </Card>

          <Card>
            <div className="page-stack" id="detailed-student-analytics">
              <h3>Детальная статистика студента</h3>
              
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap', marginBottom: '1rem' }}>
                <div style={{ flex: 1, minWidth: '250px' }}>
                  <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>
                    Выберите студента для просмотра детальной истории
                  </label>
                  <select
                    value={selectedStudentId}
                    onChange={(e) => setSelectedStudentId(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '0.5rem',
                      borderRadius: '4px',
                      border: '1px solid var(--border)',
                      background: 'var(--bg)',
                      color: 'var(--text)',
                      font: 'inherit'
                    }}
                  >
                    <option value="">-- Выберите студента --</option>
                    {analytics.students.map(s => (
                      <option key={s.id} value={s.id}>
                        {s.fullName}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {selectedStudent ? (
                <div className="page-stack" style={{ borderTop: '1px solid var(--border)', paddingTop: '1.5rem', marginTop: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                    <div>
                      <h4 style={{ margin: 0, fontSize: '1.25rem' }}>{selectedStudent.fullName}</h4>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
                        Рекомендуемая оценка: <Badge tone="info">{selectedStudent.recommendedGrade}</Badge>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '1.5rem' }}>
                      <div style={{ textAlign: 'right' }}>
                        <span className="muted" style={{ display: 'block', fontSize: '0.8rem' }}>M (Лабы)</span>
                        <strong style={{ fontSize: '1.2rem' }}>{selectedStudent.labScore}</strong>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span className="muted" style={{ display: 'block', fontSize: '0.8rem' }}>N (CTF)</span>
                        <strong style={{ fontSize: '1.2rem' }}>{selectedStudent.ctfScore}</strong>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span className="muted" style={{ display: 'block', fontSize: '0.8rem' }}>Итого (S*100)</span>
                        <strong style={{ fontSize: '1.2rem' }}>{selectedStudent.totalScore100.toFixed(1)}</strong>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1.5rem', borderBottom: '1px solid var(--border)', paddingBottom: '0.5rem' }}>
                    <button
                      type="button"
                      onClick={() => setActiveSubTab("tests")}
                      style={{
                        padding: '0.5rem 1rem',
                        border: 'none',
                        background: activeSubTab === "tests" ? 'var(--primary)' : 'transparent',
                        color: activeSubTab === "tests" ? 'white' : 'var(--text)',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        fontWeight: 600
                      }}
                    >
                      Тесты ({selectedStudentTests.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveSubTab("challenges")}
                      style={{
                        padding: '0.5rem 1rem',
                        border: 'none',
                        background: activeSubTab === "challenges" ? 'var(--primary)' : 'transparent',
                        color: activeSubTab === "challenges" ? 'white' : 'var(--text)',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        fontWeight: 600
                      }}
                    >
                      Задания CTF ({selectedStudentChallenges.length})
                    </button>
                  </div>

                  <div style={{ marginTop: '1rem' }}>
                    {activeSubTab === "tests" ? (
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
                            }
                          },
                          {
                            key: "score",
                            title: "Баллы / Порог",
                            render: (r: any) => {
                              if (r.status === "NOT_STARTED") return "—";
                              const isPassed = r.score >= r.passingScore;
                              return (
                                <span style={{ color: isPassed ? "var(--success)" : "var(--danger)", fontWeight: 'bold' }}>
                                  {r.score}% (порог: {r.passingScore}%)
                                </span>
                              );
                            }
                          },
                          {
                            key: "date",
                            title: "Время сдачи/активности",
                            render: (r: any) => r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "—"
                          }
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
                              <Badge tone={r.solved ? "success" : r.attemptsCount > 0 ? "info" : "neutral"}>
                                {r.solved ? "Решено" : r.attemptsCount > 0 ? "Есть попытки" : "Не приступал"}
                              </Badge>
                            )
                          },
                          {
                            key: "scoreAwarded",
                            title: "Получено баллов",
                            render: (r: any) => r.solved ? r.scoreAwarded : "—"
                          },
                          { key: "attemptsCount", title: "Всего попыток" },
                          {
                            key: "date",
                            title: "Время последнего действия",
                            render: (r: any) => r.date !== "-" ? new Date(r.date).toLocaleString("ru") : "—"
                          }
                        ]}
                        rows={selectedStudentChallenges}
                      />
                    )}
                  </div>
                </div>
              ) : (
                <div style={{ color: 'var(--text-secondary)', padding: '2rem 0', textAlign: 'center' }}>
                  Студент не выбран или список студентов пуст.
                </div>
              )}
            </div>
          </Card>
        </>
      )}
    </div>
  );
};
