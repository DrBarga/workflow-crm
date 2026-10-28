import type { Metadata } from "next";
import { ProfilePage } from "@views/profile";
export const metadata: Metadata = { title: "Профиль | Workflow CRM" };
export default function Page() {
  return <ProfilePage />;
}
