import { useEffect, type Dispatch } from "react";
import { ApiError } from "@shared/api";
import { loadProfile } from "../api/telegramApi";
import type { BindingAction } from "./bindingState";

export function profileError(error: unknown): Extract<BindingAction, { type: "error" }> {
  const unauthorized = error instanceof ApiError && (error.status === 401 || error.status === 403);
  return {
    type: "error",
    unauthorized,
    message: unauthorized
      ? "Войдите в систему, чтобы управлять Telegram."
      : "Не удалось обновить профиль. Попробуйте ещё раз.",
  };
}
export function useProfilePolling(dispatch: Dispatch<BindingAction>, enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let running = false;
    const refresh = async () => {
      if (running || document.visibilityState === "hidden") return;
      running = true;
      try {
        const profile = await loadProfile(controller.signal);
        if (!controller.signal.aborted) dispatch({ type: "profile", profile });
      } catch (error) {
        if (!controller.signal.aborted) dispatch(profileError(error));
      } finally {
        running = false;
      }
    };
    void refresh();
    const timer = window.setInterval(() => void refresh(), 3000);
    const onFocus = () => void refresh();
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onFocus);
    return () => {
      controller.abort();
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onFocus);
    };
  }, [dispatch, enabled]);
}
