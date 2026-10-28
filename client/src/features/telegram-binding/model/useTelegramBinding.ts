import { useEffect, useReducer, useRef } from "react";
import { ApiError } from "@shared/api";
import { requestLink, unlinkTelegram } from "../api/telegramApi";
import { bindingReducer, initialState } from "./bindingState";
import { profileError, useProfilePolling } from "./useProfilePolling";

export function useTelegramBinding() {
  const [state, dispatch] = useReducer(bindingReducer, initialState);
  const activeRequest = useRef<AbortController | null>(null);
  useProfilePolling(dispatch, !state.unlinked);
  useEffect(() => () => activeRequest.current?.abort(), []);
  useEffect(() => {
    if (!state.link) return;
    const delay = Math.max(0, Date.parse(state.link.expiresAt) - Date.now());
    const timer = window.setTimeout(() => dispatch({ type: "expired" }), delay);
    return () => window.clearTimeout(timer);
  }, [state.link]);
  const run = async (unlink: boolean) => {
    if (!state.profile || activeRequest.current) return;
    const controller = new AbortController();
    activeRequest.current = controller;
    dispatch({ type: "busy" });
    try {
      if (unlink) {
        await unlinkTelegram(controller.signal);
        if (!controller.signal.aborted) dispatch({ type: "unlinked" });
      } else {
        const link = await requestLink(controller.signal);
        if (!controller.signal.aborted)
          dispatch({ type: "link", link, baseline: state.profile.telegram.revision });
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        const action = profileError(error);
        if (error instanceof ApiError && error.status === 503)
          action.message = "Telegram временно недоступен. Попробуйте позже.";
        else if (!action.unauthorized)
          action.message = "Не удалось выполнить действие. Попробуйте ещё раз.";
        dispatch({ ...action, source: "action" });
      }
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null;
    }
  };
  return { ...state, generateLink: () => run(false), unlink: () => run(true) };
}
