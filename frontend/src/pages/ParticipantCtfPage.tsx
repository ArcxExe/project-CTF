import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useToastStore } from "@/entities/notification/model/toastStore";
import { attemptsApi } from "@/shared/api/services/attempts";
import type { AttemptHistoryEntry } from "@/shared/api/services/attempts";
import type { Challenge } from "@/shared/api/services/challenges";
import { competitionsApi } from "@/shared/api/services/competitions";
import { getCompetitionWindowState } from "@/shared/lib/competitionWindow";
import {
  buildCategorySummaries,
  filterAndSortTasks,
  getTaskCategoryLabel,
  groupTasksByCategory,
  type CategoryGroup,
  type TaskSortOption,
  type TaskStatusFilter,
} from "@/shared/lib/ctfTasks";
import { cn } from "@/shared/lib/cn";
import type { Competition } from "@/shared/types/competition";
import { Badge } from "@/shared/ui/Badge/Badge";
import { Button } from "@/shared/ui/Button/Button";
import { Card } from "@/shared/ui/Card/Card";
import { Input } from "@/shared/ui/Input/Input";
import { Loader } from "@/shared/ui/Loader/Loader";
import { PageHeader } from "@/shared/ui/PageHeader/PageHeader";
import "./pages.css";

const LANE_PREVIEW_LIMIT = 15;
const GRID_PAGE_SIZE = 36;

interface CtfTaskCardProps {
  task: Challenge;
  isSolved: boolean;
  onSelectCategory?: (categoryKey: string) => void;
}

const CtfTaskCard = ({ task, isSolved, onSelectCategory }: CtfTaskCardProps) => {
  const categoryText = task.category?.trim() || "Без категории";
  const isFirstBloodLocked = Boolean(task.isFirstBloodOnly && task.firstBloodTaken && !isSolved);

  return (
    <Card className={cn("ctf-task-card-wrap", isSolved && "task-card-solved")}>
      <div className="task-card">
        <div className="task-card__head">
          <div className="ctf-task-card__badges">
            {isSolved && <Badge tone="success">✓ Решено</Badge>}
            {isFirstBloodLocked && <Badge tone="danger">🔒 First Blood занят</Badge>}
            {task.isFirstBloodOnly && !task.firstBloodTaken && !isSolved && (
              <Badge tone="warning">First Blood Only</Badge>
            )}
            {onSelectCategory ? (
              <button
                type="button"
                className="ctf-category-tag"
                onClick={() => onSelectCategory(task.category?.trim() || "__uncategorized__")}
                title={`Показать все задания темы «${categoryText}»`}
              >
                {categoryText}
              </button>
            ) : (
              <Badge tone="info">{categoryText}</Badge>
            )}
          </div>
          <strong>{task.points} pts</strong>
        </div>

        <div>
          {task.taskCode && <div className="ctf-task-code">{task.taskCode}</div>}
          <h3 className="ctf-task-title">{task.title}</h3>
          <p className="muted ctf-task-desc">
            {task.description && task.description.length > 160
              ? `${task.description.slice(0, 160).trimEnd()}…`
              : task.description || "Описание задания отсутствует."}
          </p>
        </div>

        <div className="task-card__footer">
          <span>{task.difficulty || "—"}</span>
          <Link to={`/participant/tasks/${task.id}`}>Открыть</Link>
        </div>
      </div>
    </Card>
  );
};

interface CategoryFeedLaneProps {
  group: CategoryGroup;
  solvedIds: ReadonlySet<string>;
  onSelectCategory: (categoryKey: string) => void;
}

const CategoryFeedLane = ({ group, solvedIds, onSelectCategory }: CategoryFeedLaneProps) => {
  const trackRef = useRef<HTMLDivElement>(null);
  const visibleTasks = group.tasks.slice(0, LANE_PREVIEW_LIMIT);
  const remainingCount = group.tasks.length - visibleTasks.length;
  const allSolved = group.totalCount > 0 && group.solvedCount >= group.totalCount;

  const scrollTrack = (direction: -1 | 1) => {
    trackRef.current?.scrollBy({ left: direction * 640, behavior: "smooth" });
  };

  return (
    <section className="ctf-category-lane" aria-label={`Тема ${group.label}`}>
      <div className="ctf-category-lane__header">
        <div className="ctf-category-lane__title-group">
          <h3 className="ctf-category-lane__title">{group.label}</h3>
          <Badge tone={allSolved ? "success" : "neutral"}>
            Решено {group.solvedCount} / {group.totalCount}
          </Badge>
          {group.tasks.length !== group.totalCount && (
            <span className="muted ctf-category-lane__filtered">
              (показано {group.tasks.length})
            </span>
          )}
        </div>

        <div className="ctf-category-lane__actions">
          <div className="ctf-lane-scroll-controls">
            <button
              type="button"
              className="ctf-scroll-btn"
              onClick={() => scrollTrack(-1)}
              aria-label={`Прокрутить ленту «${group.label}» влево`}
            >
              ‹
            </button>
            <button
              type="button"
              className="ctf-scroll-btn"
              onClick={() => scrollTrack(1)}
              aria-label={`Прокрутить ленту «${group.label}» вправо`}
            >
              ›
            </button>
          </div>
          <Button variant="secondary" onClick={() => onSelectCategory(group.key)}>
            Все задания ({group.tasks.length}) →
          </Button>
        </div>
      </div>

      <div ref={trackRef} className="ctf-category-feed">
        {visibleTasks.map((task) => (
          <div key={task.id} className="ctf-category-feed__item">
            <CtfTaskCard
              task={task}
              isSolved={solvedIds.has(task.id)}
              onSelectCategory={onSelectCategory}
            />
          </div>
        ))}

        {remainingCount > 0 && (
          <div className="ctf-category-feed__item">
            <Card className="ctf-lane-more-card">
              <div className="ctf-lane-more-card__content">
                <span className="muted">В ленте показаны первые {LANE_PREVIEW_LIMIT}</span>
                <strong>Ещё {remainingCount} задач в теме «{group.label}»</strong>
                <Button onClick={() => onSelectCategory(group.key)}>
                  Открыть всю тему
                </Button>
              </div>
            </Card>
          </div>
        )}
      </div>
    </section>
  );
};

export const ParticipantCtfPage = () => {
  const { push } = useToastStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const [competitions, setCompetitions] = useState<Competition[]>([]);
  const [history, setHistory] = useState<AttemptHistoryEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<TaskStatusFilter>("all");
  const [sortBy, setSortBy] = useState<TaskSortOption>("default");
  const [visibleGridCount, setVisibleGridCount] = useState(GRID_PAGE_SIZE);

  const selectedCategory = searchParams.get("topic") ?? "";

  const setSelectedCategory = useCallback(
    (categoryKey: string) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (categoryKey) {
            next.set("topic", categoryKey);
          } else {
            next.delete("topic");
          }
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  useEffect(() => {
    setVisibleGridCount(GRID_PAGE_SIZE);
  }, [selectedCategory, searchQuery, statusFilter, sortBy]);

  useEffect(() => {
    void Promise.all([competitionsApi.getAll(), attemptsApi.getHistory()])
      .then(([compRows, historyRows]) => {
        setCompetitions(compRows);
        setHistory(historyRows);
      })
      .catch((error: unknown) => {
        push({
          title:
            error instanceof Error
              ? error.message
              : "Не удалось загрузить данные соревнований",
          variant: "error",
        });
      })
      .finally(() => setIsLoading(false));
  }, [push]);

  const solvedIds = useMemo(() => {
    const set = new Set<string>();
    for (const entry of history) {
      const challengeId = entry.challengeId || (entry as { taskId?: string }).taskId;
      const isCorrect = Boolean(entry.correct ?? (entry as { isCorrect?: boolean }).isCorrect);
      if (challengeId && isCorrect) {
        set.add(challengeId);
      }
    }
    return set;
  }, [history]);

  const activeCompetitions = useMemo(() => {
    const now = new Date();
    return competitions.filter((comp) => getCompetitionWindowState(comp, now).isWindowOpen);
  }, [competitions]);

  const allActiveTasks = useMemo(
    () => activeCompetitions.flatMap((comp) => comp.tasks ?? []),
    [activeCompetitions],
  );

  const categorySummaries = useMemo(
    () => buildCategorySummaries(allActiveTasks, solvedIds),
    [allActiveTasks, solvedIds],
  );

  const totalSolvedCount = useMemo(
    () => allActiveTasks.filter((task) => solvedIds.has(task.id)).length,
    [allActiveTasks, solvedIds],
  );

  const competitionViews = useMemo(() => {
    return activeCompetitions
      .map((comp) => {
        const compTasks = comp.tasks ?? [];
        const filteredTasks = filterAndSortTasks(compTasks, {
          categoryKey: selectedCategory,
          searchQuery,
          statusFilter,
          sortBy,
          solvedIds,
        });

        const categoryGroups = selectedCategory
          ? []
          : groupTasksByCategory(filteredTasks, compTasks, solvedIds);

        return {
          competition: comp,
          allTasksCount: compTasks.length,
          filteredTasks,
          categoryGroups,
        };
      })
      .filter((view) => view.filteredTasks.length > 0);
  }, [activeCompetitions, selectedCategory, searchQuery, statusFilter, sortBy, solvedIds]);

  const hasActiveFilters =
    Boolean(selectedCategory) ||
    Boolean(searchQuery.trim()) ||
    statusFilter !== "all" ||
    sortBy !== "default";

  const resetAllFilters = () => {
    setSelectedCategory("");
    setSearchQuery("");
    setStatusFilter("all");
    setSortBy("default");
  };

  if (isLoading) {
    return <Loader label="Загружаем задания..." />;
  }

  const hasAnyActiveTasks = allActiveTasks.length > 0;
  const totalMatchingTasks = competitionViews.reduce(
    (sum, view) => sum + view.filteredTasks.length,
    0,
  );
  const selectedCategorySummary = categorySummaries.find((c) => c.key === selectedCategory);

  return (
    <div className="page-stack">
      <PageHeader
        title="Страница CTF"
        subtitle="Выбирайте нужную тему или просматривайте ленты заданий по категориям."
        actions={
          hasAnyActiveTasks ? (
            <Badge tone="info">
              Решено {totalSolvedCount} из {allActiveTasks.length}
            </Badge>
          ) : undefined
        }
      />

      {hasAnyActiveTasks ? (
        <>
          <Card className="ctf-toolbar">
            <div className="ctf-toolbar__controls">
              <div className="ctf-toolbar__search">
                <Input
                  label="Поиск заданий"
                  placeholder="Название, код или тема..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                />
              </div>

              <div className="ui-field">
                <span className="ui-field__label">Статус</span>
                <div className="ctf-status-pills" role="group" aria-label="Фильтр по статусу">
                  {(
                    [
                      { value: "all", label: "Все" },
                      { value: "unsolved", label: "Нерешённые" },
                      { value: "solved", label: "Решённые" },
                    ] as const
                  ).map((item) => (
                    <button
                      key={item.value}
                      type="button"
                      className={cn(
                        "ctf-status-pill",
                        statusFilter === item.value && "ctf-status-pill--active",
                      )}
                      onClick={() => setStatusFilter(item.value)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>

              <label className="ui-field ctf-toolbar__sort">
                <span className="ui-field__label">Сортировка</span>
                <select
                  className="ui-input"
                  value={sortBy}
                  onChange={(event) => setSortBy(event.target.value as TaskSortOption)}
                >
                  <option value="default">По умолчанию</option>
                  <option value="difficulty-asc">Сначала легче</option>
                  <option value="difficulty-desc">Сначала сложнее</option>
                  <option value="points-asc">Баллы: по возрастанию</option>
                  <option value="points-desc">Баллы: по убыванию</option>
                </select>
              </label>
            </div>

            <div className="ctf-topic-ribbon-wrap">
              <div className="ctf-topic-ribbon__head">
                <span className="ui-field__label">
                  Темы ({categorySummaries.length})
                </span>
                {hasActiveFilters && (
                  <button
                    type="button"
                    className="ctf-reset-link"
                    onClick={resetAllFilters}
                  >
                    Сбросить фильтры
                  </button>
                )}
              </div>

              <div className="ctf-topic-ribbon" role="tablist" aria-label="Выбор темы заданий">
                <button
                  type="button"
                  role="tab"
                  aria-selected={selectedCategory === ""}
                  className={cn(
                    "ctf-topic-chip",
                    selectedCategory === "" && "ctf-topic-chip--active",
                  )}
                  onClick={() => setSelectedCategory("")}
                >
                  <span>Все темы</span>
                  <span className="ctf-topic-chip__count">
                    {totalSolvedCount}/{allActiveTasks.length}
                  </span>
                </button>

                {categorySummaries.map((cat) => {
                  const isActive = selectedCategory === cat.key;
                  const isCompleted = cat.total > 0 && cat.solved >= cat.total;

                  return (
                    <button
                      key={cat.key}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      className={cn(
                        "ctf-topic-chip",
                        isActive && "ctf-topic-chip--active",
                        isCompleted && !isActive && "ctf-topic-chip--completed",
                      )}
                      onClick={() => setSelectedCategory(isActive ? "" : cat.key)}
                    >
                      <span>
                        {isCompleted ? "✓ " : ""}
                        {cat.label}
                      </span>
                      <span className="ctf-topic-chip__count">
                        {cat.solved}/{cat.total}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          </Card>

          {selectedCategory && (
            <div className="ctf-selected-topic-bar">
              <div className="ctf-selected-topic-bar__info">
                <Button variant="secondary" onClick={() => setSelectedCategory("")}>
                  ← Все темы
                </Button>
                <div>
                  <h2>{getTaskCategoryLabel(selectedCategory)}</h2>
                  <span className="muted">
                    {selectedCategorySummary
                      ? `Решено ${selectedCategorySummary.solved} из ${selectedCategorySummary.total} · найдено ${totalMatchingTasks}`
                      : `Найдено заданий: ${totalMatchingTasks}`}
                  </span>
                </div>
              </div>
            </div>
          )}

          {competitionViews.length > 0 ? (
            <div className="page-stack" style={{ gap: "2rem" }}>
              {competitionViews.map(({ competition, filteredTasks, categoryGroups }) => (
                <div key={competition.id} className="page-stack" style={{ gap: "1.25rem" }}>
                  <div className="ctf-competition-head">
                    <div>
                      <h2>{competition.title}</h2>
                      {competition.description && (
                        <p className="muted" style={{ marginTop: "0.25rem" }}>
                          {competition.description}
                        </p>
                      )}
                    </div>
                    <Badge tone="neutral">{filteredTasks.length} заданий</Badge>
                  </div>

                  {selectedCategory ? (
                    <>
                      <div className="task-grid">
                        {filteredTasks.slice(0, visibleGridCount).map((task) => (
                          <CtfTaskCard
                            key={task.id}
                            task={task}
                            isSolved={solvedIds.has(task.id)}
                          />
                        ))}
                      </div>

                      {filteredTasks.length > visibleGridCount && (
                        <div className="ctf-load-more">
                          <Button
                            variant="secondary"
                            onClick={() =>
                              setVisibleGridCount((prev) => prev + GRID_PAGE_SIZE)
                            }
                          >
                            Показать ещё (осталось {filteredTasks.length - visibleGridCount})
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => setVisibleGridCount(filteredTasks.length)}
                          >
                            Показать все ({filteredTasks.length})
                          </Button>
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="ctf-lanes-stack">
                      {categoryGroups.map((group) => (
                        <CategoryFeedLane
                          key={group.key}
                          group={group}
                          solvedIds={solvedIds}
                          onSelectCategory={setSelectedCategory}
                        />
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <Card>
              <div className="ctf-empty-state">
                <p className="muted">По выбранным фильтрам задания не найдены.</p>
                <Button variant="secondary" onClick={resetAllFilters}>
                  Показать все темы
                </Button>
              </div>
            </Card>
          )}
        </>
      ) : (
        <Card>
          <p className="muted">Нет активных соревнований или заданий в данный момент.</p>
        </Card>
      )}
    </div>
  );
};
