import type { Identifier, ISODateTime } from "@/shared/types/common";

export type TestStatus = "draft" | "published" | "archived";
export type TestQuestionType = "single_choice" | "multiple_choice" | "text" | "code";

export interface Test {
  id: Identifier;
  title: string;
  description?: string;
  competitionId?: Identifier;
  streamIds: Identifier[];
  status: TestStatus;
  timeLimitMinutes: number;
  passingScore: number;
  attemptsLimit?: number;
  questionsCount: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface TestQuestion {
  id: Identifier;
  testId: Identifier;
  title: string;
  description?: string;
  type: TestQuestionType;
  options?: Array<{
    id: Identifier;
    label: string;
    isCorrect?: boolean;
  }>;
  points: number;
  order: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}
