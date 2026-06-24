import { useEffect, useState } from "react";
import { Card } from "@/shared/ui/Card/Card";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import { useAuthStore } from "@/features/auth/model/authStore";
import { leaderboardApi, type LeaderboardRow } from "@/shared/api/services/leaderboard";
import { attemptsApi, type AttemptHistoryEntry } from "@/shared/api/services/attempts";
import { Loader } from "@/shared/ui/Loader/Loader";
import { useToastStore } from "@/entities/notification/model/toastStore";
import "./pages.css";

export const ParticipantProfilePage = () => {
  const { currentUser } = useAuthStore();
  const { push } = useToastStore();
  const [history, setHistory] = useState<AttemptHistoryEntry[]>([]);
  const [leaderboard, setLeaderboard] = useState<LeaderboardRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    void Promise.all([
      attemptsApi.getHistory(),
      leaderboardApi.getAll()
    ]).then(([hist, lb]) => {
      setHistory(hist);
      setLeaderboard(lb);
    }).catch(() => {
      push({
        title: "Ошибка загрузки профиля",
        variant: "error"
      });
    }).finally(() => {
      setIsLoading(false);
    });
  }, [push]);

  if (isLoading) return <Loader label="Загрузка профиля..." />;

  // Calculate v2
  let v2 = "N/A";
  let myScore = 0;
  
  if (currentUser) {
    const myEntry = leaderboard.find(r => r.participant === currentUser.username || r.participant === currentUser.email || r.participant === currentUser.fullName || r.participant === currentUser.id);
    if (myEntry) {
      myScore = myEntry.score;
      const myGroup = myEntry.group;
      const groupScores = leaderboard.filter(r => r.group === myGroup).map(r => r.score);
      const maxScore = Math.max(0, ...groupScores);
      if (maxScore > 0) {
        v2 = ((myScore / maxScore) * 100).toFixed(1);
      } else {
        v2 = "0";
      }
    } else {
      // Participant not found in leaderboard (maybe hidden or 0 points)
      // Try to find if they are just 0 points but others have points?
      // Since leaderboard returns all, if not found, it's 0.
      v2 = "0";
    }
  }

  return (
    <div className="page-stack">
      <PageHeader
        title="Профиль участника"
        subtitle="Здесь отображается ваша статистика, баллы и оценка."
      />

      <div className="grid grid-2">
        <Card>
          <div className="page-stack">
            <h3>Личные данные</h3>
            <div className="info-grid">
              <span>Ник:</span>
              <strong>{currentUser?.fullName || currentUser?.email}</strong>
              <span>Баллы:</span>
              <strong>{myScore}</strong>
              <span>Оценка:</span>
              <strong>{v2}</strong>
            </div>
          </div>
        </Card>

        <Card>
          <div className="page-stack">
            <h3>История сабмитов</h3>
            {history.length > 0 ? (
              <ul className="page-list" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {history.map((item) => (
                  <li key={item.attemptId} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
                    <div>
                      <strong>{item.challengeTitle}</strong>
                      <div className="muted" style={{ fontSize: '0.85em' }}>{new Date(item.submittedAt).toLocaleString("ru-RU")}</div>
                    </div>
                    <div>
                      {item.correct ? <span style={{ color: 'var(--success)' }}>+{item.earnedPoints}</span> : <span style={{ color: 'var(--error)' }}>Неверно</span>}
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
    </div>
  );
};
