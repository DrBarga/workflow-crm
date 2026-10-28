import { createSlice } from "@reduxjs/toolkit";
import type { BackendStatusState } from "./types";
import { checkBackendHealth } from "./thunks";

const initialState: BackendStatusState = {
  status: "idle",
  message: null,
  checkedAt: null,
};

const backendStatusSlice = createSlice({
  name: "backendStatus",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(checkBackendHealth.pending, (state) => {
        state.status = "checking";
        state.message = null;
      })
      .addCase(checkBackendHealth.fulfilled, (state, action) => {
        state.status = "online";
        state.message = action.payload;
        state.checkedAt = new Date().toISOString();
      })
      .addCase(checkBackendHealth.rejected, (state, action) => {
        state.status = "offline";
        state.message = action.payload ?? action.error.message ?? "Unknown error";
        state.checkedAt = new Date().toISOString();
      });
  },
});

export const backendStatusReducer = backendStatusSlice.reducer;
