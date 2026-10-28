import type { ReactNode } from "react";
import { AppShell } from "@widgets/app-shell";

// Закрытая часть приложения: всё внутри группы получает каркас с навигацией.
// Экраны входа лежат в соседней группе и остаются без него.
export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
