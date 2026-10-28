"use client";

import { useEffect, type ReactNode } from "react";
import { loadCurrentUser, selectSession } from "@entities/session";
import { useLogout } from "@features/auth/logout";
import { useAppDispatch, useAppSelector } from "@shared/lib";
import { AppSidebar } from "./AppSidebar";
import { MobileNav } from "./MobileNav";
import styles from "./AppShell.module.css";

// Каркас закрытой части приложения. Роль берём у backend: cookie сессии
// httpOnly, и кто вошёл, клиент узнаёт только из /api/auth/me.
export function AppShell({ children }: { children: ReactNode }) {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector(selectSession);
  const { logout } = useLogout();

  useEffect(() => {
    dispatch(loadCurrentUser());
  }, [dispatch]);

  return (
    <div className={styles.shell}>
      <div className={styles.backdrop} aria-hidden />
      <AppSidebar role={user?.role} onLogout={() => void logout()} />
      <div className={styles.column}>
        <main className={styles.content}>{children}</main>
      </div>
      <MobileNav role={user?.role} />
    </div>
  );
}
