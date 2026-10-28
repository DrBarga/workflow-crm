import { SetPasswordPage } from "@views/auth/set-password";
import type { Metadata } from "next";

export const metadata: Metadata = { referrer: "no-referrer" };

export default function Page() {
  return <SetPasswordPage />;
}
