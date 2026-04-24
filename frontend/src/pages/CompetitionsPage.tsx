import { useEffect, useState } from "react";
import { competitionsApi } from "@/shared/api/services/competitions";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Card } from "@/shared/ui/Card/Card";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import type { Competition } from "@/shared/types/competition";
import "./pages.css";

export const CompetitionsPage = () => {
  const [items, setItems] = useState<Competition[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { push } = useToastStore();

  useEffect(() => {
    void competitionsApi
      .getAll()
      .then((response) => {
        setItems(response);
      })
      .catch((error: unknown) => {
        push({
          title: error instanceof Error ? error.message : "Не удалось загрузить соревнования",
          variant: "error",
        });
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [push]);

  if (isLoading) {
    return <Loader label="Загружаем соревнования..." />;
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Соревнования"
        subtitle="Стартовый список соревнований для административного модуля."
      />

      <div className="grid">
        {items.map((competition) => (
          <Card key={competition.id}>
            <div className="competition-card">
              <div>
                <h3>{competition.title}</h3>
                <p className="muted">{competition.description}</p>
              </div>

              <div className="competition-card__meta">
                <Badge tone={competition.status === "active" ? "success" : "info"}>
                  {competition.status}
                </Badge>
                <span>Старт: {new Date(competition.startsAt).toLocaleString("ru-RU")}</span>
                <span>Финиш: {new Date(competition.endsAt).toLocaleString("ru-RU")}</span>
                <span>Рейтинг: {competition.ratingVisible ? "вкл" : "выкл"}</span>
                <span>Промокоды: {competition.promoCodesEnabled ? "вкл" : "выкл"}</span>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
};
