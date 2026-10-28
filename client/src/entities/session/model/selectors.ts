import type { SessionRootState } from "./types";

export const selectSession = (state: SessionRootState) => state.session;

export const selectCurrentUser = (state: SessionRootState) => state.session.user;
