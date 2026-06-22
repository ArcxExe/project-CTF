import { useEffect, useMemo, useState } from "react";
import { studentsApi } from "@/shared/api/services/students";
import { groupsApi } from "@/shared/api/services/groups";
import { streamsApi } from "@/shared/api/services/streams";
import type { Group, Student, Stream } from "@/shared/types/education";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { useAuthStore } from "@/features/auth/model/authStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

export const StudentsPage = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [pendingStudents, setPendingStudents] = useState<Student[]>([]);
  const [allGroups, setAllGroups] = useState<Group[]>([]);
  const [allStreams, setAllStreams] = useState<Stream[]>([]);
  const [search, setSearch] = useState("");
  const [groupFilter, setGroupFilter] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  // Create Student Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [middleName, setMiddleName] = useState("");
  const [studentCode, setStudentCode] = useState("");
  const [groupId, setGroupId] = useState("");
  const { push } = useToastStore();
  const { currentUser } = useAuthStore();

  const loadData = () => {
    Promise.all([
      studentsApi.getAll().catch(() => [] as Student[]),
      studentsApi.getPending().catch(() => [] as Student[]),
      groupsApi.getAll().catch(() => [] as Group[]),
      streamsApi.getAll().catch(() => [] as Stream[]),
    ])
    .then(([allStudents, pending, fetchedGroups, fetchedStreams]) => {
      setStudents(allStudents);
      setPendingStudents(pending);
      setAllGroups(fetchedGroups);
      setAllStreams(fetchedStreams);
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

  const handleFileImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      await studentsApi.importStudents(file);
      push({ title: "Импорт успешно завершен", variant: "success" });
      loadData();
    } catch (e: unknown) {
      const errorMsg = e instanceof Error ? e.message : "Ошибка при импорте студентов";
      push({ title: errorMsg, variant: "error" });
    } finally {
      // Clear the input so the same file can be selected again
      event.target.value = "";
    }
  };

  const handleCreateStudent = async () => {
    try {
      await studentsApi.createStudent({
        firstName,
        lastName,
        middleName,
        studentCode,
        groupId: groupId || undefined,
      });
      push({ title: "Студент успешно создан", variant: "success" });
      setIsCreateModalOpen(false);
      setFirstName("");
      setLastName("");
      setMiddleName("");
      setStudentCode("");
      setGroupId("");
      loadData();
    } catch (e: unknown) {
      push({ title: "Ошибка при создании студента", variant: "error" });
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

  const groupedGroups = useMemo(() => {
    const streamMap = new Map<string, string>();
    allStreams.forEach((s) => {
      streamMap.set(s.id, s.name);
    });

    const groupsByStream: Record<string, Group[]> = {};
    const ungrouped: Group[] = [];

    allGroups.forEach((group) => {
      if (group.streamId && streamMap.has(group.streamId)) {
        if (!groupsByStream[group.streamId]) {
          groupsByStream[group.streamId] = [];
        }
        groupsByStream[group.streamId].push(group);
      } else {
        ungrouped.push(group);
      }
    });

    const result: { streamId: string | null; streamName: string; groups: Group[] }[] = [];

    allStreams.forEach((stream) => {
      const groups = groupsByStream[stream.id] || [];
      if (groups.length > 0) {
        result.push({
          streamId: stream.id,
          streamName: stream.name,
          groups: groups.sort((a, b) => a.name.localeCompare(b.name)),
        });
      }
    });

    if (ungrouped.length > 0) {
      result.push({
        streamId: null,
        streamName: "Без потока",
        groups: ungrouped.sort((a, b) => a.name.localeCompare(b.name)),
      });
    }

    return result;
  }, [allGroups, allStreams]);

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
        actions={
          <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
            <input
              type="file"
              accept=".csv,.xlsx"
              id="student-import-input"
              style={{ display: "none" }}
              onChange={handleFileImport}
            />
            <Button onClick={() => document.getElementById("student-import-input")?.click()} variant="secondary">
              Импорт
            </Button>
            <Button onClick={() => setIsCreateModalOpen(true)}>
              Добавить студента
            </Button>
          </div>
        }
      />

      <Modal
        open={isCreateModalOpen}
        title="Создание студента"
        onClose={() => setIsCreateModalOpen(false)}
      >
        <div className="page-stack">
          <Input
            label="Имя"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
          />
          <Input
            label="Фамилия"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
          />
          <Input
            label="Отчество"
            value={middleName}
            onChange={(e) => setMiddleName(e.target.value)}
          />
          <Input
            label="Код студента (ИСУ и т.п.)"
            value={studentCode}
            onChange={(e) => setStudentCode(e.target.value)}
          />
          <label className="ui-field">
            <span className="ui-field__label">Академическая группа</span>
            <select
              className="ui-input"
              value={groupId}
              onChange={(e) => setGroupId(e.target.value)}
            >
              <option value="">
                Выберите группу
              </option>
              {groupedGroups.map((grouping) => (
                <optgroup key={grouping.streamId || "no-stream"} label={grouping.streamName}>
                  {grouping.groups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.name}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem", marginTop: "1rem" }}>
            <Button variant="secondary" onClick={() => setIsCreateModalOpen(false)}>
              Отмена
            </Button>
            <Button
              onClick={handleCreateStudent}
              disabled={!firstName || !lastName || !studentCode}
            >
              Создать
            </Button>
          </div>
        </div>
      </Modal>

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
                { key: "actions", title: "Действия", render: (s) => {
                  const isCreatedByMe = s.createdBy === currentUser?.id;
                  return (
                    <div style={{display: "flex", gap: "0.5rem"}}>
                      <Button onClick={() => handleApprove(s.id)} disabled={isCreatedByMe}>Подтвердить</Button>
                      <Button variant="danger" onClick={() => handleReject(s.id)} disabled={isCreatedByMe}>Отклонить</Button>
                    </div>
                  );
                } }
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
                    disabled={student.createdBy === currentUser?.id}
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
