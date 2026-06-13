const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";
const AUTH_TOKEN_KEY = "ctf-access-token";

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

const getErrorMessage = (body: unknown, fallback: string) => {
  if (!body || typeof body !== "object") {
    return fallback;
  }

  const errorBody = body as ApiErrorBody;
  if (errorBody.validationErrors) {
    return Object.values(errorBody.validationErrors).join(", ");
  }

  return errorBody.message ?? errorBody.error ?? fallback;
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
      authTokenStorage.clear();
    }

    throw new Error(getErrorMessage(body, `HTTP ${response.status}`));
  }

  return body as T;
}
