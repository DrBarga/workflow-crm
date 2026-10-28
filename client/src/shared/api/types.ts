// Формат ошибок backend — dev.workflowcrm.shared.error.ApiError.
export interface FieldViolation {
  field: string;
  message: string;
}

export interface ApiErrorPayload {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId: string;
  violations: FieldViolation[];
}

export function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (typeof value !== "object" || value === null) return false;

  const candidate = value as Partial<ApiErrorPayload>;
  return (
    typeof candidate.status === "number" &&
    typeof candidate.code === "string" &&
    typeof candidate.message === "string"
  );
}
