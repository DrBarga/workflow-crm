"use client";

import { useAppSelector } from "@shared/lib";
import { selectSession } from "../model/selectors";

const ROLE_LABEL: Record<string, string> = {
  SUPERADMIN: "Суперадміністратор",
  CEO: "CEO",
  DEVELOPMENT_DIRECTOR: "Директор з розвитку",
  REGIONAL_MANAGER: "Регіональний менеджер",
  MANAGER: "Менеджер",
  CONTRACT_MANAGER: "Менеджер з договорів",
};

export function CurrentUserBadge() {
  const { status, user } = useAppSelector(selectSession);

  if (status === "loading" || status === "unknown") {
    return <span className="text-sm opacity-60">Завантажуємо профіль…</span>;
  }

  if (!user) {
    return <span className="text-sm opacity-60">Сеанс не активний</span>;
  }

  return (
    <span className="text-sm">
      {user.name} <span className="opacity-60">· {ROLE_LABEL[user.role] ?? user.role}</span>
    </span>
  );
}
