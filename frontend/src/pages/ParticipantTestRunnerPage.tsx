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
  const [sequenceInputs, setSequenceInputs] = useState<Record<string, string>>({});
  
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
              const parsed = JSON.parse(savedAnswers) as Record<string, string[]>;
              setAnswers(parsed);

              // Initialize sequenceInputs from parsed answers
              const inputs: Record<string, string> = {};
              loadedQuestions.forEach(q => {
                if (q.type === "SEQUENCE") {
                  const ans = parsed[q.id] || [];
                  const indices = ans.map((id: string) => {
                    const idx = q.options.findIndex(opt => opt.id === id);
                    return idx !== -1 ? String(idx + 1) : "";
                  }).filter(Boolean);
                  inputs[q.id] = indices.join(", ");
                }
              });
              setSequenceInputs(inputs);
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
  const handleSequenceInputChange = (questionId: string, text: string, options: any[]) => {
    setSequenceInputs(prev => ({ ...prev, [questionId]: text }));

    const tokens = text.split(/[\s,]+/).filter(Boolean);
    const selectedIds: string[] = [];
    tokens.forEach(token => {
      const num = parseInt(token, 10);
      if (!isNaN(num) && num >= 1 && num <= options.length) {
        const opt = options[num - 1];
        if (opt && !selectedIds.includes(opt.id)) {
          selectedIds.push(opt.id);
        }
      }
    });

    setAnswers(prev => ({ ...prev, [questionId]: selectedIds }));
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
                    <p className="muted" style={{ fontSize: "0.85rem", marginBottom: "0.5rem" }}>
                      Введите порядок элементов (номера через запятую или пробел, например: 1,3,2,4).
                    </p>
                    <input 
                      style={{ padding: '0.5rem', width: '100%', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}
                      placeholder="Например: 1, 3, 2, 4" 
                      value={sequenceInputs[q.id] || ""}
                      onChange={(e) => handleSequenceInputChange(q.id, e.target.value, q.options)} 
                    />
                    <ol style={{ paddingLeft: "1.5rem", marginTop: '0.8rem', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                      {q.options.map((opt) => (
                        <li key={opt.id} style={{ color: 'var(--text-secondary)' }}>
                          {opt.text}
                        </li>
                      ))}
                    </ol>
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
