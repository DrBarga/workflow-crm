import { apiPost, apiPostWithCsrf, apiFetch } from "@shared/api";
import type { SessionUser } from "@entities/session";

/** Статусы challenge: dev.workflowcrm.auth.domain.ChallengeStatus. */
export type ChallengeStatus =
  | "PENDING_TELEGRAM"
  | "PENDING_EMAIL"
  | "BINDING_REQUIRED"
  | "APPROVED"
  | "REJECTED"
  | "CONSUMED"
  | "EXPIRED";

export interface Challenge {
  challengeId: string;
  status: ChallengeStatus;
  expiresInSeconds: number;
}

// Логин отвечает 200 с пользователем либо 202 с challenge. Различаем по
// полю: заголовки ответа apiFetch наружу не отдаёт.
export type LoginResult = { user: SessionUser } | { challenge: Challenge };

export async function login(email: string, password: string): Promise<LoginResult> {
  const body = await apiPost<SessionUser | Challenge>("/api/auth/login", { email, password });

  return "challengeId" in body ? { challenge: body } : { user: body };
}

export function fetchChallengeStatus(id: string): Promise<{ status: ChallengeStatus }> {
  return apiFetch(`/api/auth/challenges/${id}`);
}

export function confirmEmailCode(
  id: string,
  code: string,
): Promise<{ status: "APPROVED" | "BINDING_REQUIRED"; bindingLink: string }> {
  return apiPostWithCsrf(`/api/auth/challenges/${id}/email`, { code });
}

export function completeChallenge(id: string): Promise<SessionUser> {
  return apiPostWithCsrf(`/api/auth/challenges/${id}/complete`);
}

export function resendChallenge(id: string): Promise<void> {
  return apiPostWithCsrf(`/api/auth/challenges/${id}/resend`);
}
