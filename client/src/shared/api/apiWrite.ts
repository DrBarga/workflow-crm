import { apiFetch } from "./apiClient";

export async function apiWrite<T>(
  path: string,
  method: "POST" | "DELETE",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const csrf = await apiFetch<{ headerName: string; token: string }>("/api/auth/csrf", {
    cache: "no-store",
    signal,
  });
  return apiFetch<T>(path, {
    method,
    signal,
    cache: "no-store",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
