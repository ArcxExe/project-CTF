import { useEffect, useMemo, useState } from "react";
import { studentsApi } from "@/shared/api/services/students";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { Modal } from "@/shared/ui/Modal/Modal";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import type { Student } from "@/shared/types/education";
import "./pages.css";

export const StudentsPage = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const { push } = useToastStore();

  useEffect(() => {
    void studentsApi
      .getAll()
      .then((response) => {
        setStudents(response);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить список студентов",
          variant: "error",
        });
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [push]);

  const filteredStudents = useMemo(
    () =>
      students.filter((student) =>
        [student.fullName, student.group, student.stream, student.nickname]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase()),
      ),
    [search, students],
  );

  if (isLoading) {
    return <Loader label="Загружаем список студентов..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Студенты"
        subtitle="Базовый экран списка студентов с mock API."
        actions={<Button onClick={() => setIsModalOpen(true)}>Добавить студента</Button>}
      />

      <Card>
        <Input
          label="Поиск"
          placeholder="ФИО, группа, поток, ник"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </Card>

      <DataTable
        columns={[
          { key: "fullName", title: "ФИО" },
          { key: "nickname", title: "Ник" },
          { key: "group", title: "Группа" },
          { key: "stream", title: "Поток" },
          { key: "laboratoryScore", title: "Лабораторные" },
          {
            key: "status",
            title: "Статус",
            render: (student) => {
              const tone =
                student.status === "active"
                  ? "success"
                  : student.status === "blocked" || student.status === "disqualified"
                    ? "danger"
                    : "info";

              return <Badge tone={tone}>{student.status}</Badge>;
            },
          },
        ]}
        rows={filteredStudents}
      />

      <Modal open={isModalOpen} title="Заглушка формы" onClose={() => setIsModalOpen(false)}>
        <p className="muted">
          Здесь можно разместить форму создания студента и потом подключить реальный POST endpoint.
        </p>
      </Modal>
    </div>
  );
};
