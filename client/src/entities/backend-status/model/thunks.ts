import { createAsyncThunk } from "@reduxjs/toolkit";
import { ApiError } from "@shared/api";
import { fetchBackendHealth } from "../api/backendStatusApi";

export const checkBackendHealth = createAsyncThunk<string, void, { rejectValue: string }>(
  "backendStatus/check",
  async (_, { rejectWithValue }) => {
    try {
      const health = await fetchBackendHealth();
      return health.status;
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Network error";
      return rejectWithValue(message);
    }
  },
);
