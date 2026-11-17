const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";
const AUTH_TOKEN_KEY = "ctf-access-token";

type UnauthorizedListener = () => void;
const unauthorizedListeners = new Set<UnauthorizedListener>();

export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
}

function notifyUnauthorized(): void {
  authTokenStorage.clear();
  unauthorizedListeners.forEach((listener) => {
    try {
      listener();
    } catch (err) {
      console.error("Error in onUnauthorized listener:", err);
    }
  });

  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("auth:unauthorized"));
  }
}

interface ApiErrorBody {
  message?: string;
  error?: string;
  validationErrors?: Record<string, string>;
}

export const authTokenStorage = {
  get(): string | null {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  },

  set(token: string): void {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
  },

  clear(): void {
    localStorage.removeItem(AUTH_TOKEN_KEY);
  },
};

const buildUrl = (path: string) => {
  if (path.startsWith("http")) {
    return path;
  }

  return `${API_BASE_URL}${path}`;
};

export function buildSseUrl(path: string, params?: Record<string, string | undefined>): string {
  const base = path.startsWith("http") ? path : `${API_BASE_URL}${path}`;
  const [urlPath, queryString] = base.split("?");
  const searchParams = new URLSearchParams(queryString || "");

  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) {
        searchParams.set(key, value);
      }
    }
  }

  const token = authTokenStorage.get();
  if (token) {
    searchParams.set("token", token);
  }

  const qs = searchParams.toString();
  return qs ? `${urlPath}?${qs}` : urlPath;
}

const FIELD_LABELS: Record<string, string> = {
  name: "Название",
  title: "Название",
  streamId: "Поток",
  groupId: "Группа",
  studentId: "Студент",
  email: "Email",
  password: "Пароль",
  username: "Имя пользователя",
  studentCode: "Шифр студента",
  flag: "Флаг",
  baseScore: "Баллы",
  code: "Код",
};

const localizeMessage = (raw: string): string => {
  const trimmed = raw.trim();
  const map: Record<string, string> = {
    "Invalid credentials": "Неверный логин (email) или пароль. Проверьте введённые данные.",
    "Bad credentials": "Неверный логин (email) или пароль. Проверьте введённые данные.",
    "User is blocked": "Учётная запись заблокирована. Обратитесь к преподавателю или администратору.",
    "Student account is blocked": "Учётная запись студента заблокирована. Обратитесь к администратору.",
    "User account is blocked": "Учётная запись заблокирована. Обратитесь к администратору.",
    "Email already registered": "Этот email уже зарегистрирован.",
    "Username already taken": "Это имя пользователя уже занято.",
    "Student code not registered": "Шифр / токен студента не найден в списке курса.",
    "Invalid student code": "Шифр / токен студента не найден в списке курса.",
    "This student code is already linked": "Этот шифр студента уже привязан к другой учётной записи.",
    "Student code already claimed": "Этот шифр студента уже привязан к другой учётной записи.",
    "Student not found": "Профиль студента не найден.",
    "Competition not found": "Соревнование не найдено.",
    "Competition is not available": "Соревнование недоступно.",
    "startDate cannot be after endDate": "Дата начала соревнования не может быть позже даты окончания.",
    "Quiz not found": "Тест не найден.",
    "Quiz is not published": "Тест ещё не опубликован.",
    "Test is not published": "Тест ещё не опубликован.",
    "Active quiz attempt is required to view questions":
      "Для просмотра вопросов необходимо начать попытку прохождения теста.",
    "No active attempt found for this quiz": "Активная попытка прохождения теста не найдена.",
    "Quiz time limit exceeded": "Время на прохождение теста истекло.",
    "Invalid promo code": "Промокод не найден или введён неверно. Проверьте код и попробуйте снова.",
    "Promo code already used by this student": "Вы уже использовали этот промокод ранее.",
    "Promo code usage limit reached": "Лимит использований этого промокода исчерпан.",
    "Group not found": "Группа не найдена.",
    "Group exists with this name": "Группа с таким названием уже существует.",
    "Group with this name already exists": "Группа с таким названием уже существует.",
    "Access Denied": "Недостаточно прав для выполнения операции.",
    "Access is denied": "Недостаточно прав для выполнения операции.",
    "HTTP 401": "Требуется авторизация. Войдите в систему заново.",
    "HTTP 403": "Недостаточно прав для выполнения операции.",
    "HTTP 404": "Запрошенный ресурс не найден.",
    "HTTP 500": "Внутренняя ошибка сервера. Повторите попытку позже.",
    "Unexpected server error": "Внутренняя ошибка сервера. Повторите попытку позже.",
    "Validation failed": "Проверьте правильность заполнения полей формы.",
  };

  if (map[trimmed]) {
    return map[trimmed];
  }
  if (trimmed.startsWith("Student is not allowed to start quiz in status:")) {
    return "Прохождение теста недоступно для текущего статуса вашей учётной записи.";
  }
  if (trimmed.startsWith("Student not found:")) {
    return "Профиль студента не найден.";
  }
  return trimmed;
};

const getErrorMessage = (body: unknown, fallback: string) => {
  if (!body || typeof body !== "object") {
    return localizeMessage(fallback);
  }

  const errorBody = body as ApiErrorBody;
  if (errorBody.validationErrors && Object.keys(errorBody.validationErrors).length > 0) {
    return Object.entries(errorBody.validationErrors)
      .map(([field, msg]) => {
        const label = FIELD_LABELS[field] || field;
        const localizedMsg =
          msg === "не должно равняться null" || msg === "must not be null"
            ? "обязательно для заполнения"
            : msg === "не должно быть пустым" || msg === "must not be blank"
            ? "не может быть пустым"
            : localizeMessage(msg);
        return `Поле «${label}»: ${localizedMsg}`;
      })
      .join("; ");
  }

  const raw = errorBody.message ?? errorBody.error ?? fallback;
  return localizeMessage(raw);
};

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = authTokenStorage.get();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(buildUrl(path), {
    ...init,
    headers,
  });

  const contentType = response.headers.get("Content-Type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();

  if (!response.ok) {
    if (response.status === 401) {
      notifyUnauthorized();
    }

    throw new Error(getErrorMessage(body, `HTTP ${response.status}`));
  }

  return body as T;
}
