"use client";

import { useAppSelector } from "@shared/lib";
import { selectBackendStatus } from "../model/selectors";

const STATUS_LABEL: Record<string, string> = {
  idle: "Not checked yet",
  checking: "Checking connection…",
  online: "Backend is online",
  offline: "Backend is unreachable",
};

const STATUS_COLOR: Record<string, string> = {
  idle: "var(--color-foreground)",
  checking: "var(--color-accent)",
  online: "var(--color-success)",
  offline: "var(--color-danger)",
};

export function BackendStatusBadge() {
  const { status, message } = useAppSelector(selectBackendStatus);

  return (
    <div className="flex items-center gap-2 text-sm">
      <span
        aria-hidden
        className="inline-block size-2 rounded-full"
        style={{ backgroundColor: STATUS_COLOR[status] }}
      />
      <span>{STATUS_LABEL[status]}</span>
      {message ? <span className="text-xs opacity-60">({message})</span> : null}
    </div>
  );
}
