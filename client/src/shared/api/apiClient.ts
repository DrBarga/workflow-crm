import { env } from "../config";
import { isApiErrorPayload, type FieldViolation } from "./types";

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly violations: FieldViolation[];
  /** Идентификатор запроса из ответа backend — с ним ищут запись в логах. */
  readonly correlationId: string | null;

  constructor(
    message: string,
    code: string,
    status: number,
    violations: FieldViolation[] = [],
    correlationId: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.violations = violations;
    this.correlationId = correlationId;
  }
}

async function parseErrorResponse(response: Response): Promise<ApiError> {
  const body = await response.json().catch(() => null);

  if (isApiErrorPayload(body)) {
    // Тайп-гард проверяет только status, code и message, поэтому violations читаем
    // недоверчиво: чужой формат иначе уронит обработчик ошибок формы.
    const violations = Array.isArray(body.violations)
      ? body.violations.filter(
          (item) => item && typeof item.field === "string" && typeof item.message === "string",
        )
      : [];
    return new ApiError(
      body.message,
      body.code,
      response.status,
      violations,
      body.correlationId ?? null,
    );
  }

  return new ApiError(response.statusText || "Request failed", "UNKNOWN_ERROR", response.status);
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  // 204 и другие ответы без тела: вызывающий код типизирует их как void.
  if (response.status === 204 || response.headers.get("Content-Length") === "0") {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
