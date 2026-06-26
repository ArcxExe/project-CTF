import { useState, useEffect, useMemo } from "react";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { Card } from "@/shared/ui/Card/Card";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Input } from "@/shared/ui/Input/Input";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { analyticsApi, AnalyticsSummary, TaskAnalytics, StudentTestAnalytics } from "@/shared/api/services/analytics";
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
      const [aData, tData, stData] = await Promise.all([
        analyticsApi.getGroupAnalytics(selectedGroupId, selectedFlowId),
        analyticsApi.getTaskAnalytics(selectedGroupId, selectedFlowId),
        analyticsApi.getStudentTestAnalytics(selectedGroupId, selectedFlowId)
      ]);
      setAnalytics(aData);
      setTasks(tData);
      setStudentTests(stData);
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
                { key: "fullName", title: "ФИО" },
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
        </>
      )}
    </div>
  );
};
