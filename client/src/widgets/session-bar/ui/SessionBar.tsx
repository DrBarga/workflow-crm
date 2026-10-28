"use client";

import { CurrentUserBadge } from "@entities/session";

// Кто вошёл. Профиль запрашивает каркас приложения, здесь только показ:
// выход живёт в боковом рельсе.
export function SessionBar() {
  return (
    <div className="flex items-center justify-end gap-3 border-b border-black/[.06] px-4 py-2">
      <CurrentUserBadge />
    </div>
  );
}
