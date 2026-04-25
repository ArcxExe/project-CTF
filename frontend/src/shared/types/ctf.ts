import type { Identifier, ISODateTime } from "@/shared/types/common";

export type TaskDifficulty = "easy" | "medium" | "hard";
export type TaskStatus = "draft" | "published" | "archived";
export type AttemptStatus = "pending" | "accepted" | "rejected" | "manual_review";
export type PromoCodeStatus = "active" | "expired" | "disabled";
export type ScoreAdjustmentReason =
  | "manual_bonus"
  | "manual_penalty"
  | "laboratory_score"
  | "promo_code"
  | "sanction";

export interface Category {
  id: Identifier;
  name: string;
  slug: string;
  description?: string;
  color?: string;
  order: number;
  tasksCount?: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface CTFTask {
  id: Identifier;
  competitionId: Identifier;
  categoryId: Identifier;
  title: string;
  slug: string;
  description: string;
  points: number;
  difficulty: TaskDifficulty;
  status: TaskStatus;
  attachments?: Array<{
    id: Identifier;
    name: string;
    url: string;
  }>;
  hints?: Array<{
    id: Identifier;
    text: string;
    cost?: number;
  }>;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface Attempt {
  id: Identifier;
  taskId: Identifier;
  studentId: Identifier;
  submittedFlag: string;
  status: AttemptStatus;
  isCorrect: boolean;
  pointsAwarded: number;
  reviewerId?: Identifier;
  submittedAt: ISODateTime;
  reviewedAt?: ISODateTime;
}

export interface PromoCode {
  id: Identifier;
  code: string;
  competitionId?: Identifier;
  title: string;
  description?: string;
  bonusPoints?: number;
  bonusAttempts?: number;
  hintIds?: Identifier[];
  maxUses?: number;
  usedCount: number;
  status: PromoCodeStatus;
  expiresAt?: ISODateTime;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface ScoreAdjustment {
  id: Identifier;
  studentId: Identifier;
  competitionId?: Identifier;
  amount: number;
  reason: ScoreAdjustmentReason;
  comment?: string;
  createdByUserId: Identifier;
  createdAt: ISODateTime;
}
