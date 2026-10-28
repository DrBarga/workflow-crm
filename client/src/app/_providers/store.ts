import { configureStore } from "@reduxjs/toolkit";
import { backendStatusReducer } from "@entities/backend-status";
import { sessionReducer } from "@entities/session";

export function makeStore() {
  return configureStore({
    reducer: {
      backendStatus: backendStatusReducer,
      session: sessionReducer,
    },
  });
}

export type AppStore = ReturnType<typeof makeStore>;
