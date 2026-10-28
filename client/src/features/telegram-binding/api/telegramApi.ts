import type { Profile } from "@entities/profile";
import { apiFetch, apiWrite } from "@shared/api";

export interface TelegramLink {
  link: string;
  expiresAt: string;
  expiresInSeconds: number;
}
export const loadProfile = (signal: AbortSignal) =>
  apiFetch<Profile>("/api/profile", { cache: "no-store", signal });
export const requestLink = (signal: AbortSignal) =>
  apiWrite<TelegramLink>("/api/profile/telegram/link", "POST", undefined, signal);
export const unlinkTelegram = (signal: AbortSignal) =>
  apiWrite<void>("/api/profile/telegram", "DELETE", undefined, signal);
