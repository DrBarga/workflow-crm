"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError } from "@shared/api";
import { takeTokenFromUrl } from "@shared/lib";
import { resetPassword } from "../api/passwordResetApi";

type ResetState = "checking" | "ready" | "saving" | "done" | "expired";

export function usePasswordReset() {
  const [state, setState] = useState<ResetState>("checking");
  const [error, setError] = useState<string>();
  const token = useRef<string | null>(null);

  // Проверить ссылку заранее нельзя: эндпоинта для этого нет, токен
  // расходуется только при смене пароля. Поэтому ждём ответа на сохранение.
  //
  // Читаем строго один раз: takeTokenFromUrl убирает токен из адреса, а в
  // dev React вызывает эффект дважды — второй вызов увидел бы пустую строку
  // и принял живую ссылку за просроченную.
  useEffect(() => {
    if (token.current === null) token.current = takeTokenFromUrl();
    setState(token.current ? "ready" : "expired");
  }, []);

  async function submit(password: string) {
    if (state !== "ready") return;

    setState("saving");
    setError(undefined);
    try {
      await resetPassword(token.current ?? "", password);
      token.current = "";
      setState("done");
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 410) {
        setState("expired");
        return;
      }
      setError(
        reason instanceof ApiError && reason.status === 400
          ? "Пароль не відповідає вимогам. Перевірте перелік нижче."
          : "Не вдалося зберегти пароль. Спробуйте ще раз.",
      );
      setState("ready");
    }
  }

  return { state, error, submit };
}
