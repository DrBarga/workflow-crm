"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import { safeNextPath } from "@shared/lib";
import { login, type Challenge } from "../api/loginApi";
import { isAccessDenied, isLoginBlocked, loginErrorMessage } from "../lib/loginErrors";

export type LoginStep =
  | { kind: "form" }
  | { kind: "challenge"; challenge: Challenge; email: string }
  | { kind: "locked"; email: string }
  | { kind: "denied"; email: string };

export function useLoginFlow() {
  const router = useRouter();
  const [step, setStep] = useState<LoginStep>({ kind: "form" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  /*
   * Перед уходом возвращаем шаг к форме. Если cookie сессии ещё не доехала,
   * охранник в proxy.ts вернёт на /login — тот же маршрут, состояние React
   * сохранится, и экран «Відкриваємо систему…» остался бы навсегда.
   *
   * refresh нужен, чтобы серверные компоненты перечитали cookie.
   *
   * Адрес возврата читаем из строки браузера, а не через useSearchParams:
   * тот переводит страницу в client-side rendering и требует Suspense.
   */
  const finish = useCallback(() => {
    const next = new URLSearchParams(window.location.search).get("next");
    setStep({ kind: "form" });
    router.replace(safeNextPath(next));
    router.refresh();
  }, [router]);

  async function submit(email: string, password: string) {
    setSubmitting(true);
    setError(undefined);
    try {
      const result = await login(email, password);
      if ("user" in result) {
        finish();
        return;
      }
      setStep({ kind: "challenge", challenge: result.challenge, email });
    } catch (reason) {
      if (isLoginBlocked(reason)) setStep({ kind: "locked", email });
      else if (isAccessDenied(reason)) setStep({ kind: "denied", email });
      else setError(loginErrorMessage(reason));
    } finally {
      setSubmitting(false);
    }
  }

  function backToForm() {
    setStep({ kind: "form" });
    setError(undefined);
  }

  return { step, submitting, error, submit, finish, backToForm };
}
