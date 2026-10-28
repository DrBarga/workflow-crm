import Link from "next/link";

import { BackendStatusPanel } from "@widgets/backend-status-panel";
import { SessionBar } from "@widgets/session-bar";

export function HomePage() {
  return (
    <>
      <SessionBar />
      <main className="flex flex-1 flex-col items-center justify-center gap-8 px-4 py-16 text-center">
        <div>
          <h1 className="text-2xl font-semibold">Workflow CRM</h1>
          <p className="mt-2 text-sm opacity-70">
            Frontend skeleton — FSD layers, Redux Toolkit, api-client.
          </p>
        </div>
        <Link href="/profile" className="inline-block underline underline-offset-4">
          Профиль
        </Link>
        <BackendStatusPanel />
      </main>
    </>
  );
}
