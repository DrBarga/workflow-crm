import { ApiError } from "@shared/api";

// Сообщения для пользователя. Текст backend не показываем: он английский
// и описывает причину подробнее, чем стоит показывать до входа.
const MESSAGES: Record<string, string> = {
  AUTHENTICATION_FAILED: "Невірна пошта або пароль.",
  ACCESS_DENIED: "Доступ заборонено. Зверніться до адміністратора системи.",
  VALIDATION_FAILED: "Перевірте пошту й пароль.",
  INVALID_REQUEST: "Перевірте пошту й пароль.",
};

/** 429 — вход временно заблокирован, форму заменяет отдельный экран. */
export function isLoginBlocked(error: unknown): boolean {
  return error instanceof ApiError && error.status === 429;
}

/**
 * 403 — заблокировано устройство или отключён аккаунт. Backend отвечает на оба
 * случая одним кодом ACCESS_DENIED, поэтому экран один и формулировка общая.
 */
export function isAccessDenied(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}

export function loginErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Немає звʼязку із сервером. Перевірте підключення та спробуйте ще раз.";
  }

  // 503 — канал подтверждения не настроен на сервере (нет SMTP и Telegram).
  // Пользователь сам это не починит, поэтому отправляем к администратору.
  if (error.status === 503) {
    return "Підтвердження входу тимчасово недоступне. Зверніться до адміністратора системи.";
  }

  return MESSAGES[error.code] ?? "Не вдалося увійти. Спробуйте ще раз.";
}

export function challengeErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Немає звʼязку із сервером. Перевірте підключення та спробуйте ще раз.";
  }

  if (error.status === 429) {
    return "Запит надсилали щойно. Зачекайте кілька секунд і спробуйте ще раз.";
  }

  if (error.status === 400 || error.status === 401 || error.code === "VALIDATION_FAILED") {
    return "Невірний код. Перевірте останній лист і спробуйте ще раз.";
  }

  return "Не вдалося підтвердити вхід. Спробуйте ще раз.";
}
