"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { endSession } from "@entities/session";
import { useAppDispatch } from "@shared/lib";

export function useLogout() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const [leaving, setLeaving] = useState(false);

  async function logout() {
    setLeaving(true);
    // Даже если запрос не дошёл, уводим на вход: cookie могла истечь.
    // refresh — чтобы серверные компоненты перечитали cookie.
    await dispatch(endSession());
    router.replace("/login");
    router.refresh();
  }

  return { logout, leaving };
}
