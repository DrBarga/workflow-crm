"use client";

import { useAppDispatch, useAppSelector } from "@shared/lib";
import { checkBackendHealth, selectBackendStatus } from "@entities/backend-status";

export function RetryConnectionButton() {
  const dispatch = useAppDispatch();
  const { status } = useAppSelector(selectBackendStatus);
  const isChecking = status === "checking";

  function handleClick() {
    dispatch(checkBackendHealth());
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={isChecking}
      className="rounded-full border border-solid border-black/[.08] px-4 py-2 text-sm transition-colors hover:bg-black/[.04] disabled:opacity-50 dark:border-white/[.145] dark:hover:bg-white/[.06]"
    >
      {isChecking ? "Checking…" : "Check connection again"}
    </button>
  );
}
