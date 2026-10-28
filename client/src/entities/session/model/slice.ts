import { createSlice } from "@reduxjs/toolkit";
import { endSession, loadCurrentUser } from "./thunks";
import type { SessionState } from "./types";

const initialState: SessionState = {
  status: "unknown",
  user: null,
};

const sessionSlice = createSlice({
  name: "session",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadCurrentUser.pending, (state) => {
        state.status = "loading";
      })
      .addCase(loadCurrentUser.fulfilled, (state, action) => {
        state.status = "authenticated";
        state.user = action.payload;
      })
      .addCase(loadCurrentUser.rejected, (state) => {
        state.status = "anonymous";
        state.user = null;
      })
      .addCase(endSession.fulfilled, (state) => {
        state.status = "anonymous";
        state.user = null;
      });
  },
});

export const sessionReducer = sessionSlice.reducer;
