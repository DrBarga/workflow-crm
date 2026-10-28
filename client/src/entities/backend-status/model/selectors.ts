import type { BackendStatusRootState } from "./types";

export const selectBackendStatus = (state: BackendStatusRootState) => state.backendStatus;
