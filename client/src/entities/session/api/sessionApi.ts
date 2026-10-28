import { apiFetch, apiPostWithCsrf } from "@shared/api";
import type { SessionUser } from "../model/types";

export function fetchCurrentUser(): Promise<SessionUser> {
  return apiFetch<SessionUser>("/api/auth/me");
}

export function logout(): Promise<void> {
  return apiPostWithCsrf("/api/auth/logout");
}
