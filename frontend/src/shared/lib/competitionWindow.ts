import type { Competition } from "@/shared/types/competition";

export interface CompetitionWindowState {
  startDate: Date | null;
  endDate: Date | null;
  isActiveStatus: boolean;
  isNotStarted: boolean;
  isEnded: boolean;
  isWindowOpen: boolean;
}

/**
 * Parses an ISO date string into a valid Date, returning null for empty or invalid values.
 * Never passes null to `new Date(null)` (which yields 1970-01-01 in JavaScript).
 */
export const parseCompetitionDate = (value: string | null | undefined): Date | null => {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

export const isCompetitionActiveStatus = (status: Competition["status"]): boolean =>
  status === "active" || status === "published";

/**
 * Evaluates competition submission window state, mirroring backend AttemptService logic:
 * - startsAt == null means already started
 * - endsAt == null means no time limit
 */
export const getCompetitionWindowState = (
  competition: Pick<Competition, "status" | "startsAt" | "endsAt">,
  now: Date = new Date(),
): CompetitionWindowState => {
  const startDate = parseCompetitionDate(competition.startsAt);
  const endDate = parseCompetitionDate(competition.endsAt);
  const isActiveStatus = isCompetitionActiveStatus(competition.status);
  const nowMs = now.getTime();
  const isNotStarted = startDate !== null && nowMs < startDate.getTime();
  const isEnded = endDate !== null && nowMs > endDate.getTime();
  const isWindowOpen = isActiveStatus && !isNotStarted && !isEnded;

  return {
    startDate,
    endDate,
    isActiveStatus,
    isNotStarted,
    isEnded,
    isWindowOpen,
  };
};

export const formatCompetitionStart = (value: string | null | undefined): string => {
  const date = parseCompetitionDate(value);
  return date ? date.toLocaleString("ru-RU") : "Не указано";
};

export const formatCompetitionEnd = (value: string | null | undefined): string => {
  const date = parseCompetitionDate(value);
  return date ? date.toLocaleString("ru-RU") : "Без ограничения";
};
