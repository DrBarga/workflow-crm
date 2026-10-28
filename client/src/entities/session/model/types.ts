// Ответ LoginController/MeController: dev.workflowcrm.auth.api.LoginResponse.
export interface SessionUser {
  id: string;
  role: string;
  /** Регион пользователя, null у ролей без привязки к региону. */
  region: string | null;
  name: string;
}

export type SessionStatus = "unknown" | "loading" | "authenticated" | "anonymous";

export interface SessionState {
  status: SessionStatus;
  user: SessionUser | null;
}

export interface SessionRootState {
  session: SessionState;
}
