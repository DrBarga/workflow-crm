import { apiFetch, apiWrite } from "@shared/api";

interface OpenInvitationResponse {
  deviceStatus: "PENDING_PASSWORD_SETUP";
}

export function openPasswordInvitation(token: string): Promise<OpenInvitationResponse> {
  return apiFetch(`/api/auth/invite/${encodeURIComponent(token)}`, { cache: "no-store" });
}

export function acceptPasswordInvitation(token: string, password: string): Promise<void> {
  return apiWrite("/api/auth/set-password", "POST", { token, password });
}
