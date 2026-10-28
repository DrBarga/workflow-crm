import type { Profile } from "@entities/profile";
import type { TelegramLink } from "../api/telegramApi";

export interface BindingState {
  profile: Profile | null;
  loading: boolean;
  busy: boolean;
  link: TelegramLink | null;
  baseline: string | null;
  error: string;
  errorSource: "profile" | "action" | null;
  notice: string;
  unlinked: boolean;
}
export const initialState: BindingState = {
  profile: null,
  loading: true,
  busy: false,
  link: null,
  baseline: null,
  error: "",
  errorSource: null,
  notice: "",
  unlinked: false,
};
export type BindingAction =
  | { type: "profile"; profile: Profile }
  | { type: "error"; message: string; unauthorized: boolean; source?: "action" }
  | { type: "busy" }
  | { type: "link"; link: TelegramLink; baseline: string | null }
  | { type: "expired" }
  | { type: "unlinked" };
export function bindingReducer(state: BindingState, action: BindingAction): BindingState {
  switch (action.type) {
    case "profile": {
      const changedUser = state.profile !== null && state.profile.id !== action.profile.id;
      const confirmed =
        !changedUser &&
        state.link &&
        action.profile.telegram.linked &&
        action.profile.telegram.revision !== state.baseline;
      return {
        ...state,
        profile: action.profile,
        loading: false,
        error: changedUser || state.errorSource === "profile" ? "" : state.error,
        errorSource: changedUser || state.errorSource === "profile" ? null : state.errorSource,
        link: confirmed || changedUser ? null : state.link,
        notice: confirmed ? "Telegram подключён." : changedUser ? "" : state.notice,
      };
    }
    case "error":
      return {
        ...state,
        loading: false,
        busy: action.source === "action" ? false : state.busy,
        error: action.message,
        errorSource: action.source ?? "profile",
        profile: action.unauthorized ? null : state.profile,
        link: action.unauthorized ? null : state.link,
      };
    case "busy":
      return { ...state, busy: true, error: "", errorSource: null, notice: "" };
    case "link":
      return { ...state, busy: false, link: action.link, baseline: action.baseline };
    case "expired":
      return { ...state, link: null, notice: "Ссылка истекла. Получите новую ссылку." };
    case "unlinked":
      return {
        ...initialState,
        loading: false,
        unlinked: true,
        notice: "Telegram отключён. Войдите в систему повторно.",
      };
  }
}
