import type { Challenge } from "@/shared/api/services/challenges";

export const UNCATEGORIZED_KEY = "__uncategorized__";
export const UNCATEGORIZED_LABEL = "Без категории";

export type TaskStatusFilter = "all" | "unsolved" | "solved";
export type TaskSortOption =
  | "default"
  | "difficulty-asc"
  | "difficulty-desc"
  | "points-asc"
  | "points-desc";

export interface CategorySummary {
  key: string;
  label: string;
  total: number;
  solved: number;
}

export interface CategoryGroup {
  key: string;
  label: string;
  tasks: Challenge[];
  totalCount: number;
  solvedCount: number;
}

/** Returns the normalized category key for a CTF task. */
export const getTaskCategoryKey = (task: Challenge): string => {
  const trimmed = task.category?.trim();
  return trimmed ? trimmed : UNCATEGORIZED_KEY;
};

/** Returns a human-readable category label for a normalized category key. */
export const getTaskCategoryLabel = (key: string): string =>
  key === UNCATEGORIZED_KEY ? UNCATEGORIZED_LABEL : key;

const compareCategoryKeys = (a: string, b: string): number => {
  if (a === b) return 0;
  if (a === UNCATEGORIZED_KEY) return 1;
  if (b === UNCATEGORIZED_KEY) return -1;
  return a.localeCompare(b, "ru", { sensitivity: "base" });
};

const DIFFICULTY_RANK: Record<string, number> = {
  easy: 1,
  легкая: 1,
  лёгкая: 1,
  medium: 2,
  средняя: 2,
  hard: 3,
  сложная: 3,
};

const getDifficultyRank = (difficulty?: string): number => {
  if (!difficulty) return 99;
  return DIFFICULTY_RANK[difficulty.trim().toLowerCase()] ?? 99;
};

/** Aggregates unique categories across tasks with total and solved counts. */
export const buildCategorySummaries = (
  tasks: Challenge[],
  solvedIds: ReadonlySet<string>,
): CategorySummary[] => {
  const map = new Map<string, { total: number; solved: number }>();

  for (const task of tasks) {
    const key = getTaskCategoryKey(task);
    const current = map.get(key) ?? { total: 0, solved: 0 };
    current.total += 1;
    if (solvedIds.has(task.id)) {
      current.solved += 1;
    }
    map.set(key, current);
  }

  return Array.from(map.entries())
    .sort(([keyA], [keyB]) => compareCategoryKeys(keyA, keyB))
    .map(([key, counts]) => ({
      key,
      label: getTaskCategoryLabel(key),
      total: counts.total,
      solved: counts.solved,
    }));
};

/** Filters tasks by category, search query, and solved status, then sorts them. */
export const filterAndSortTasks = (
  tasks: Challenge[],
  options: {
    categoryKey: string;
    searchQuery: string;
    statusFilter: TaskStatusFilter;
    sortBy: TaskSortOption;
    solvedIds: ReadonlySet<string>;
  },
): Challenge[] => {
  const normalizedQuery = options.searchQuery.trim().toLowerCase();

  const filtered = tasks.filter((task) => {
    if (options.categoryKey && getTaskCategoryKey(task) !== options.categoryKey) {
      return false;
    }

    const isSolved = options.solvedIds.has(task.id);
    if (options.statusFilter === "solved" && !isSolved) return false;
    if (options.statusFilter === "unsolved" && isSolved) return false;

    if (normalizedQuery) {
      const haystack = [task.title, task.description, task.taskCode, task.category]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      if (!haystack.includes(normalizedQuery)) {
        return false;
      }
    }

    return true;
  });

  if (options.sortBy === "default") {
    return filtered;
  }

  return [...filtered].sort((a, b) => {
    switch (options.sortBy) {
      case "points-asc":
        return a.points - b.points;
      case "points-desc":
        return b.points - a.points;
      case "difficulty-asc": {
        const diffDelta = getDifficultyRank(a.difficulty) - getDifficultyRank(b.difficulty);
        return diffDelta !== 0 ? diffDelta : a.points - b.points;
      }
      case "difficulty-desc": {
        const diffDelta = getDifficultyRank(b.difficulty) - getDifficultyRank(a.difficulty);
        return diffDelta !== 0 ? diffDelta : b.points - a.points;
      }
      default:
        return 0;
    }
  });
};

/**
 * Groups filtered tasks by category while keeping total/solved stats
 * from the unfiltered competition task list for accurate progress badges.
 */
export const groupTasksByCategory = (
  filteredTasks: Challenge[],
  allCompetitionTasks: Challenge[],
  solvedIds: ReadonlySet<string>,
): CategoryGroup[] => {
  const statsByKey = new Map<string, { total: number; solved: number }>();
  for (const task of allCompetitionTasks) {
    const key = getTaskCategoryKey(task);
    const stat = statsByKey.get(key) ?? { total: 0, solved: 0 };
    stat.total += 1;
    if (solvedIds.has(task.id)) {
      stat.solved += 1;
    }
    statsByKey.set(key, stat);
  }

  const grouped = new Map<string, Challenge[]>();
  for (const task of filteredTasks) {
    const key = getTaskCategoryKey(task);
    const list = grouped.get(key);
    if (list) {
      list.push(task);
    } else {
      grouped.set(key, [task]);
    }
  }

  return Array.from(grouped.entries())
    .sort(([keyA], [keyB]) => compareCategoryKeys(keyA, keyB))
    .map(([key, tasks]) => {
      const stats = statsByKey.get(key) ?? { total: tasks.length, solved: 0 };
      return {
        key,
        label: getTaskCategoryLabel(key),
        tasks,
        totalCount: stats.total,
        solvedCount: stats.solved,
      };
    });
};
