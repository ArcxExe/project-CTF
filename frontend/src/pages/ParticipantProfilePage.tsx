import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { useAuthStore } from "@/features/auth/model/authStore";
import "./pages.css";

export const ParticipantProfilePage = () => {
  const { currentUser } = useAuthStore();

  return (
    <div className="page-stack">
      <PageHeader
        title="Профиль участника"
        subtitle="Здесь позже будут баллы, группа, поток, результаты и доступ к этапам."
      />

      <Card>
        <div className="page-stack">
          <div>
            <strong>ФИО</strong>
            <p className="muted">{currentUser?.fullName}</p>
          </div>
          <div>
            <strong>Email</strong>
            <p className="muted">{currentUser?.email}</p>
          </div>
          <div>
            <strong>Роль</strong>
            <p className="muted">{currentUser?.role}</p>
          </div>
        </div>
      </Card>
    </div>
  );
};
