import { apiPost } from "@shared/api";

// 410 — токен истёк, использован или подделан: backend отвечает одинаково
// на все эти случаи (password-recovery.md).
export function resetPassword(token: string, password: string): Promise<void> {
  return apiPost("/api/auth/reset-password", { token, password });
}
