import { apiFetch } from "./apiClient";

interface CsrfToken {
  headerName: string;
  token: string;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: "POST",
    headers: JSON_HEADERS,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/*
 * CSRF-токен лежит в httpOnly cookie, поэтому его выдаёт отдельный эндпоинт,
 * а имя заголовка приходит вместе с ним.
 *
 * Токен нужен всем POST, кроме login, forgot-password, reset-password и
 * вебхука Telegram — для них он отключён на backend (CookieCsrfProtection).
 */
export async function apiPostWithCsrf<T>(path: string, body?: unknown): Promise<T> {
  const csrf = await apiFetch<CsrfToken>("/api/auth/csrf");

  return apiFetch<T>(path, {
    method: "POST",
    headers: { ...JSON_HEADERS, [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
