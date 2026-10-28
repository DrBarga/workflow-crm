import { createAsyncThunk } from "@reduxjs/toolkit";
import { fetchCurrentUser, logout } from "../api/sessionApi";
import type { SessionUser } from "./types";

// 401 здесь не ошибка приложения, а обычное «не авторизован»: срез
// переводится в anonymous, редирект делает страница.
export const loadCurrentUser = createAsyncThunk<SessionUser>("session/load", () =>
  fetchCurrentUser(),
);

export const endSession = createAsyncThunk<void>("session/end", () => logout());
