import { apiPost } from "@shared/api";

// 202 приходит и на несуществующий адрес: backend намеренно не подсказывает,
// есть ли такой аккаунт (password-recovery.md).
export function requestPasswordReset(email: string): Promise<void> {
  return apiPost("/api/auth/forgot-password", { email });
}
