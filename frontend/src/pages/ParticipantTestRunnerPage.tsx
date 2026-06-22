import { useEffect, useState, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { testsApi } from "@/shared/api/services/tests";
import type { QuizQuestion, QuizAttempt, CtfTest } from "@/shared/api/services/tests";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

export const ParticipantTestRunnerPage = () => {
  const { testId } = useParams();
  const navigate = useNavigate();
  const { push } = useToastStore();

  const [test, setTest] = useState<CtfTest | null>(null);
  const [attempt, setAttempt] = useState<QuizAttempt | null>(null);
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [answers, setAnswers] = useState<Record<string, string[]>>({});
  
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);

  useEffect(() => {
    if (!testId) return;

    const loadData = async () => {
      try {
        const publishedTests = await testsApi.getPublished();
        const currentTest = publishedTests.find(t => t.id === testId);
        if (!currentTest) throw new Error("Тест не найден");
        setTest(currentTest);

        const loadedQuestions = await testsApi.getQuestions(testId);
        setQuestions(loadedQuestions);

        const newAttempt = await testsApi.startQuiz(testId);
        setAttempt(newAttempt);

        // Load saved answers from local storage if available
        if (newAttempt && newAttempt.status === "IN_PROGRESS") {
          const savedAnswers = localStorage.getItem(`quiz_answers_${testId}`);
          if (savedAnswers) {
            try {
              setAnswers(JSON.parse(savedAnswers));
            } catch (e) {
              console.error("Failed to parse saved answers", e);
            }
          }
        }

      } catch (error) {
        push({
          title: error instanceof Error ? error.message : "Ошибка загрузки теста",
          variant: "error",
        });
        navigate("/participant/test");
      } finally {
        setIsLoading(false);
      }
    };

    void loadData();
  }, [testId, navigate, push]);

  // Timer effect
  useEffect(() => {
    if (!attempt || !test || attempt.status === "COMPLETED") return;

    const endTime = new Date(attempt.startedAt).getTime() + test.timeLimitMinutes * 60000;

    const updateTimer = () => {
      const remaining = Math.max(0, endTime - Date.now());
      setTimeLeft(remaining);
      if (remaining === 0) {
        void handleAutoSubmit();
      }
    };

    updateTimer(); // Initial call
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [attempt, test]);

  const handleSubmit = async (isAuto = false) => {
    if (!attempt || attempt.status === "COMPLETED" || isSubmitting || !testId) return;

    setIsSubmitting(true);
    try {
      const result = await testsApi.submitAnswers(testId, answers);
      setAttempt(result);
      if (testId) {
        localStorage.removeItem(`quiz_answers_${testId}`);
      }
      push({
        title: `Ответы ${isAuto ? 'автоматически отправлены' : 'сохранены'}!`,
        variant: "success",
      });
    } catch (error) {
      push({
        title: error instanceof Error ? error.message : "Ошибка при отправке ответов",
        variant: "error",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAutoSubmit = useCallback(async () => {
    await handleSubmit(true);
  }, [attempt, answers, isSubmitting]);

  // Save answers to local storage whenever they change
  useEffect(() => {
    if (attempt && attempt.status === "IN_PROGRESS" && testId) {
      localStorage.setItem(`quiz_answers_${testId}`, JSON.stringify(answers));
    }
  }, [answers, attempt, testId]);

  const handleAnswerChange = (questionId: string, value: string, type: "RADIO" | "CHECKBOX" | "SEQUENCE") => {
    setAnswers(prev => {
      let newAnswers;
      if (type === "RADIO") {
        newAnswers = { ...prev, [questionId]: [value] };
      } else if (type === "CHECKBOX") {
        const current = prev[questionId] || [];
        const next = current.includes(value) ? current.filter(v => v !== value) : [...current, value];
        newAnswers = { ...prev, [questionId]: next };
      } else {
        newAnswers = prev;
      }
      return newAnswers;
    });
  };

  const formatTime = (ms: number) => {
    const totalSeconds = Math.floor(ms / 1000);
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  if (isLoading) return <Loader label="Подготовка теста..." />;
  if (!test) return null;

  const isTimerDanger = timeLeft !== null && timeLeft <= 60000; // < 1 min

  return (
    <div className="page-stack">
      <div className="test-runner-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <PageHeader title={test.title} subtitle={test.description} />
        {attempt?.status === "IN_PROGRESS" && timeLeft !== null && (
          <div className={`test-timer ${isTimerDanger ? 'danger pulse' : ''}`} style={{ fontSize: '1.5rem', fontWeight: 'bold', color: isTimerDanger ? 'red' : 'inherit' }}>
            Осталось: {formatTime(timeLeft)}
          </div>
        )}
      </div>

      {attempt?.status === "COMPLETED" ? (
        <Card>
          <div className="page-stack">
            <h2>Тест завершен</h2>
            <p>Ваш результат: <strong>{attempt?.score} баллов</strong></p>
            <Button onClick={() => navigate("/participant/test")}>Вернуться к списку тестов</Button>
          </div>
        </Card>
      ) : (
        <div className="page-stack">
          {questions.map((q, index) => (
            <Card key={q.id}>
              <div className="page-stack">
                <h3>{index + 1}. {q.text}</h3>
                <span className="muted">{q.points} баллов</span>

                {q.type === "RADIO" && (
                  <div className="test-options" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {q.options.map(opt => (
                      <label key={opt.id} className="test-option-label" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <input
                          type="radio"
                          name={`question-${q.id}`}
                          value={opt.id}
                          checked={(answers[q.id] || [])[0] === opt.id}
                          onChange={() => handleAnswerChange(q.id, opt.id, "RADIO")}
                        />
                        {opt.text}
                      </label>
                    ))}
                  </div>
                )}

                {q.type === "CHECKBOX" && (
                  <div className="test-options" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {q.options.map(opt => (
                      <label key={opt.id} className="test-option-label" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <input
                          type="checkbox"
                          value={opt.id}
                          checked={(answers[q.id] || []).includes(opt.id)}
                          onChange={() => handleAnswerChange(q.id, opt.id, "CHECKBOX")}
                        />
                        {opt.text}
                      </label>
                    ))}
                  </div>
                )}

                {q.type === "SEQUENCE" && (
                  <div className="test-options">
                    <p className="muted" style={{ fontSize: "0.85rem" }}>
                      *(Временный UI для Sequence) Введите порядок ID через запятую.
                    </p>
                    <input 
                      style={{ padding: '0.5rem', width: '100%' }}
                      placeholder="e.g. uuid1,uuid2" 
                      value={(answers[q.id] || []).join(",")}
                      onChange={(e) => setAnswers(prev => ({ ...prev, [q.id]: e.target.value.split(",").filter(Boolean) }))} 
                    />
                    <ul style={{ paddingLeft: "1.5rem", marginTop: '0.5rem' }}>
                      {q.options.map(opt => (
                        <li key={opt.id} className="muted">{opt.id} - {opt.text}</li>
                      ))}
                    </ul>
                  </div>
                )}

              </div>
            </Card>
          ))}

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
            <Button onClick={() => void handleSubmit(false)} disabled={isSubmitting}>
              {isSubmitting ? "Отправка..." : "Завершить тест"}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};
