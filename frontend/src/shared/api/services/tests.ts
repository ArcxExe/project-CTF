import { apiRequest } from "@/shared/api/client";
import { toChallenge } from "@/shared/api/services/challenges";
import type { BackendChallengeResponse, Challenge, SubmitFlagResponse } from "@/shared/api/services/challenges";

export interface CtfTest {
  id: string;
  competitionId?: string;
  title: string;
  description: string;
  status: "draft" | "published" | "archived";
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface QuizOption {
  id: string;
  questionId: string;
  text: string;
  sequenceOrder?: number;
  isCorrect?: boolean;
}

export interface QuizQuestion {
  id: string;
  testId: string;
  type: "RADIO" | "CHECKBOX" | "SEQUENCE";
  text: string;
  points: number;
  ordering: number;
  options: QuizOption[];
}

export interface QuizAttempt {
  id: string;
  quizId: string;
  studentId: string;
  startedAt: string;
  completedAt?: string;
  score: number;
  status: "IN_PROGRESS" | "COMPLETED";
}

export interface CtfTestPayload {
  competitionId?: string;
  title: string;
  description?: string;
  status?: CtfTest["status"];
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
}

interface BackendTestResponse {
  id: string;
  competitionId?: string;
  title: string;
  description?: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  timeLimitMinutes: number;
  passingScore: number;
  questionsCount: number;
  createdAt: string;
  updatedAt: string;
}

const statusMap: Record<BackendTestResponse["status"], CtfTest["status"]> = {
  DRAFT: "draft",
  PUBLISHED: "published",
  ARCHIVED: "archived",
};

const backendStatusMap: Record<CtfTest["status"], BackendTestResponse["status"]> = {
  draft: "DRAFT",
  published: "PUBLISHED",
  archived: "ARCHIVED",
};

const toTest = (response: BackendTestResponse): CtfTest => ({
  id: response.id,
  competitionId: response.competitionId,
  title: response.title,
  description: response.description ?? "",
  status: statusMap[response.status],
  timeLimitMinutes: response.timeLimitMinutes,
  passingScore: response.passingScore,
  questionsCount: response.questionsCount,
  createdAt: response.createdAt,
  updatedAt: response.updatedAt,
});

const toBackendPayload = (payload: CtfTestPayload) => ({
  competitionId: payload.competitionId || undefined,
  title: payload.title,
  description: payload.description,
  status: payload.status ? backendStatusMap[payload.status] : undefined,
  timeLimitMinutes: payload.timeLimitMinutes,
  passingScore: payload.passingScore,
  questionsCount: payload.questionsCount,
});

export const testsApi = {
  async getPublished(): Promise<CtfTest[]> {
    const response = await apiRequest<BackendTestResponse[]>("/api/tests");
    return response.map(toTest);
  },

  async getAttempts(): Promise<QuizAttempt[]> {
    return apiRequest<QuizAttempt[]>("/api/quizzes/attempts");
  },

  async getChallenges(testId: string): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>(`/api/tests/${testId}/challenges`);
    return response.map(toChallenge);
  },

  async submitChallengeFlag(testId: string, challengeId: string, flag: string): Promise<SubmitFlagResponse> {
    return apiRequest<SubmitFlagResponse>(`/api/tests/${testId}/challenges/${challengeId}/submit`, {
      method: "POST",
      body: JSON.stringify({ flag }),
    });
  },

  async getQuestions(testId: string): Promise<QuizQuestion[]> {
    const response = await apiRequest<any[]>(`/api/quizzes/${testId}/questions`);
    return response.map(item => ({
      id: item.id,
      testId: item.testId,
      type: item.type,
      text: item.text,
      points: item.points,
      ordering: item.ordering,
      options: (item.options || []).map((opt: any) => ({
        id: opt.id,
        questionId: opt.questionId,
        text: opt.optionText,
        sequenceOrder: opt.sequenceOrder
      }))
    }));
  },

  async startQuiz(testId: string): Promise<QuizAttempt> {
    return apiRequest<QuizAttempt>(`/api/quizzes/${testId}/start`, {
      method: "POST"
    });
  },

  async submitAnswers(quizId: string, answers: Record<string, string[]>): Promise<QuizAttempt> {
    // Transform Record<string, string[]> to List<QuestionAnswerDTO> expected by the backend
    const payload = Object.entries(answers).map(([questionId, ans]) => ({
      questionId,
      answers: ans
    }));

    return apiRequest<QuizAttempt>(`/api/quizzes/${quizId}/submit`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }
};

export const adminTestsApi = {
  async getAll(): Promise<CtfTest[]> {
    const response = await apiRequest<BackendTestResponse[]>("/api/admin/tests");
    return response.map(toTest);
  },

  async create(payload: CtfTestPayload): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>("/api/admin/tests", {
      method: "POST",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toTest(response);
  },

  async getChallenges(testId: string): Promise<Challenge[]> {
    const response = await apiRequest<BackendChallengeResponse[]>(`/api/admin/tests/${testId}/challenges`);
    return response.map(toChallenge);
  },

  async addChallenge(testId: string, challengeId: string): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>(`/api/admin/tests/${testId}/challenges/${challengeId}`, {
      method: "POST",
      body: JSON.stringify({}),
    });
    return toTest(response);
  },

  async removeChallenge(testId: string, challengeId: string): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>(`/api/admin/tests/${testId}/challenges/${challengeId}`, {
      method: "DELETE",
    });
    return toTest(response);
  },

  async update(id: string, payload: CtfTestPayload): Promise<CtfTest> {
    const response = await apiRequest<BackendTestResponse>(`/api/admin/tests/${id}`, {
      method: "PUT",
      body: JSON.stringify(toBackendPayload(payload)),
    });
    return toTest(response);
  },

  async delete(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/tests/${id}`, {
      method: "DELETE",
    });
  },

  async getAdminQuestions(testId: string): Promise<QuizQuestion[]> {
    const response = await apiRequest<any[]>(`/api/admin/quizzes/tests/${testId}/questions`);
    return response.map((entry) => ({
      id: entry.question.id,
      testId: entry.question.testId,
      type: entry.question.type,
      text: entry.question.text,
      points: entry.question.points,
      ordering: entry.question.ordering,
      options: (entry.options || []).map((o: any) => ({
        id: o.id,
        questionId: o.questionId,
        text: o.optionText || "",
        isCorrect: o.correct !== undefined ? o.correct : o.isCorrect,
        sequenceOrder: o.sequenceOrder,
      })),
    }));
  },

  async addQuestion(testId: string, payload: { type: string, text: string, points: number, ordering: number }): Promise<QuizQuestion> {
    const response = await apiRequest<any>(`/api/admin/quizzes/tests/${testId}/questions`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return {
      id: response.id,
      testId: response.testId,
      type: response.type,
      text: response.text,
      points: response.points,
      ordering: response.ordering,
      options: [],
    };
  },

  async updateQuestion(id: string, payload: Partial<QuizQuestion>): Promise<QuizQuestion> {
    const response = await apiRequest<any>(`/api/admin/quizzes/questions/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    return {
      id: response.id,
      testId: response.testId,
      type: response.type,
      text: response.text,
      points: response.points,
      ordering: response.ordering,
      options: [],
    };
  },

  async deleteQuestion(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/quizzes/questions/${id}`, {
      method: "DELETE",
    });
  },

  async addOption(questionId: string, payload: { optionText: string, isCorrect: boolean, sequenceOrder?: number }): Promise<QuizOption> {
    const response = await apiRequest<any>(`/api/admin/quizzes/questions/${questionId}/options`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return {
      id: response.id,
      questionId: response.questionId,
      text: response.optionText || "",
      isCorrect: response.correct !== undefined ? response.correct : response.isCorrect,
      sequenceOrder: response.sequenceOrder,
    };
  },

  async updateOption(id: string, payload: Partial<QuizOption>): Promise<QuizOption> {
    const body: any = {};
    if (payload.text !== undefined) body.optionText = payload.text;
    if (payload.isCorrect !== undefined) body.correct = payload.isCorrect;
    if (payload.sequenceOrder !== undefined) body.sequenceOrder = payload.sequenceOrder;
    if (payload.questionId !== undefined) body.questionId = payload.questionId;

    const response = await apiRequest<any>(`/api/admin/quizzes/options/${id}`, {
      method: "PUT",
      body: JSON.stringify(body),
    });
    return {
      id: response.id,
      questionId: response.questionId,
      text: response.optionText || "",
      isCorrect: response.correct !== undefined ? response.correct : response.isCorrect,
      sequenceOrder: response.sequenceOrder,
    };
  },

  async deleteOption(id: string): Promise<void> {
    await apiRequest<void>(`/api/admin/quizzes/options/${id}`, {
      method: "DELETE",
    });
  },
};
