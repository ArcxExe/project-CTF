import { useEffect, useMemo, useState } from "react";
import { studentsApi } from "@/shared/api/services/students";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import type { Student } from "@/shared/types/education";
import "./pages.css";

export const StudentsPage = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [pendingStudents, setPendingStudents] = useState<Student[]>([]);
  const [search, setSearch] = useState("");
  const [groupFilter, setGroupFilter] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const { push } = useToastStore();

  const loadData = () => {
    Promise.all([
      studentsApi.getAll(),
      studentsApi.getPending()
    ])
    .then(([allStudents, pending]) => {
      setStudents(allStudents);
      setPendingStudents(pending);
    })
    .catch((error: unknown) => {
      push({
        title: error instanceof Error ? error.message : "Не удалось загрузить списки студентов",
        variant: "error",
      });
    })
    .finally(() => {
      setIsLoading(false);
    });
  };

  useEffect(() => {
    loadData();
  }, [push]);

  const handleUpdateStatus = async (id: string, status: string) => {
    try {
      await studentsApi.updateStatus(id, status);
      push({ title: "Статус обновлен", variant: "success" });
      loadData();
    } catch (e) {
      push({ title: "Ошибка обновления статуса", variant: "error" });
    }
  };

  const handleApprove = async (id: string) => {
    try {
      await studentsApi.approveStudent(id);
      push({ title: "Студент подтвержден", variant: "success" });
      loadData();
    } catch (e) {
      push({ title: "Ошибка", variant: "error" });
    }
  };

  const handleReject = async (id: string) => {
    try {
      await studentsApi.rejectStudent(id);
      push({ title: "Студент отклонен", variant: "success" });
      loadData();
    } catch (e) {
      push({ title: "Ошибка", variant: "error" });
    }
  };

  const groups = useMemo(() => Array.from(new Set(students.map(s => s.group))), [students]);

  const filteredStudents = useMemo(
    () =>
      students.filter((student) => {
        const matchesSearch = [student.fullName, student.group, student.stream, student.nickname]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase());
        const matchesGroup = groupFilter ? student.group === groupFilter : true;
        return matchesSearch && matchesGroup;
      }),
    [search, groupFilter, students],
  );

  if (isLoading) {
    return <Loader label="Загружаем списки..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Студенты"
        subtitle="Управление студентами, регистрациями и статусами."
      />

      {pendingStudents.length > 0 && (
        <Card>
          <div className="page-stack">
            <h3>Ожидающие подтверждения</h3>
            <DataTable
              columns={[
                { key: "fullName", title: "ФИО" },
                { key: "nickname", title: "Ник" },
                { key: "email", title: "Email" },
                { key: "group", title: "Группа" },
                { key: "actions", title: "Действия", render: (s) => (
                  <div style={{display: "flex", gap: "0.5rem"}}>
                    <Button onClick={() => handleApprove(s.id)}>Подтвердить</Button>
                    <Button variant="danger" onClick={() => handleReject(s.id)}>Отклонить</Button>
                  </div>
                ) }
              ]}
              rows={pendingStudents}
            />
          </div>
        </Card>
      )}

      <Card>
        <div className="admin-toolbar" style={{display: "flex", gap: "1rem"}}>
          <div style={{flex: 1}}>
            <Input
              label="Поиск"
              placeholder="ФИО, группа, поток, ник"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div style={{width: "200px"}}>
            <label className="ui-field">
              <span className="ui-field__label">Группа</span>
              <select className="ui-input" value={groupFilter} onChange={e => setGroupFilter(e.target.value)}>
                <option value="">Все группы</option>
                {groups.map(g => <option key={g} value={g}>{g}</option>)}
              </select>
            </label>
          </div>
        </div>
      </Card>

      <DataTable
        columns={[
          { key: "fullName", title: "ФИО" },
          { key: "nickname", title: "Ник" },
          { key: "group", title: "Группа" },
          {
            key: "status",
            title: "Статус",
            render: (student) => {
              const tone = student.status === "active" ? "success" : student.status === "blocked" || student.status === "disqualified" ? "danger" : "info";
              return (
                <div style={{display: "flex", alignItems: "center", gap: "0.5rem"}}>
                  <Badge tone={tone}>{student.status}</Badge>
                  <select
                    className="ui-input"
                    style={{ padding: "0.25rem", height: "auto", width: "auto" }}
                    value={student.status}
                    onChange={(e) => handleUpdateStatus(student.id, e.target.value)}
                  >
                    <option value="active">Активен</option>
                    <option value="blocked">Заблокирован</option>
                    <option value="out_of_rating">Вне рейтинга</option>
                    <option value="disqualified">Дисквалифицирован</option>
                  </select>
                </div>
              );
            }
          },
        ]}
        rows={filteredStudents}
      />
    </div>
  );
};
