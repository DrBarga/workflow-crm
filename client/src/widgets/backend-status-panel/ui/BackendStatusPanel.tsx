"use client";

import { useEffect } from "react";
import { useAppDispatch } from "@shared/lib";
import { checkBackendHealth, BackendStatusBadge } from "@entities/backend-status";
import { RetryConnectionButton } from "@features/check-backend-connection";

export function BackendStatusPanel() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(checkBackendHealth());
  }, [dispatch]);

  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-black/[.08] px-6 py-4 dark:border-white/[.145]">
      <BackendStatusBadge />
      <RetryConnectionButton />
    </div>
  );
}
