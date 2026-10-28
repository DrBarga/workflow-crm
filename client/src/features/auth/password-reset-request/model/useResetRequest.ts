"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@shared/api";
import { requestPasswordReset } from "../api/passwordRecoveryApi";

export const RESEND_COOLDOWN_SECONDS = 45;

function errorMessage(reason: unknown): string {
  if (!(reason instanceof ApiError)) {
    return "Немає звʼязку із сервером. Перевірте підключення та спробуйте ще раз.";
  }
  if (reason.status === 503) {
    return "Надсилання листів тимчасово недоступне. Зверніться до адміністратора системи.";
  }
  if (reason.status === 400) return "Пошта вказана з помилкою.";

  return "Не вдалося надіслати посилання. Спробуйте ще раз.";
}

export function useResetRequest() {
  const [sentTo, setSentTo] = useState<string>();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string>();
  const [resendIn, setResendIn] = useState(0);

  useEffect(() => {
    if (resendIn <= 0) return;

    const timer = setTimeout(() => setResendIn((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  async function submit(email: string) {
    setSending(true);
    setError(undefined);
    try {
      await requestPasswordReset(email);
      setSentTo(email);
      setResendIn(RESEND_COOLDOWN_SECONDS);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setSending(false);
    }
  }

  function changeEmail() {
    setSentTo(undefined);
    setError(undefined);
  }

  return { sentTo, sending, error, resendIn, submit, changeEmail };
}
