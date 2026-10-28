export type BackendConnectionStatus = "idle" | "checking" | "online" | "offline";

export interface BackendStatusState {
  status: BackendConnectionStatus;
  message: string | null;
  checkedAt: string | null;
}

export interface BackendStatusRootState {
  backendStatus: BackendStatusState;
}
