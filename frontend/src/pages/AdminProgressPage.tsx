import { useEffect, useState } from "react";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { adminProgressApi } from "@/shared/api/services/adminProgress";
import type { AdminProgressRow } from "@/shared/api/services/adminProgress";
import { Card } from "@/shared/ui/Card/Card";
import { DataTable } from "@/shared/ui/DataTable/DataTable";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

export const AdminProgressPage = () => {
  const { push } = useToastStore();
  const [rows, setRows] = useState<AdminProgressRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void adminProgressApi
      .getAll()
      .then(setRows)
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить прогресс",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем прогресс..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Прогресс участников"
        subtitle="Результаты, количество решенных задач и число попыток по каждому студенту."
      />

      <Card>
        <DataTable
          columns={[
            { key: "participant", title: "Участник" },
            { key: "group", title: "Группа" },
            { key: "score", title: "Баллы" },
            { key: "solved", title: "Решено" },
            { key: "attempts", title: "Попытки" },
          ]}
          rows={rows}
        />
      </Card>
    </div>
  );
};
