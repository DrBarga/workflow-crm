import Link from "next/link";
import { TelegramBindingPanel } from "@features/telegram-binding";

export function ProfilePage() {
  return (
    <main className="mx-auto min-h-screen max-w-2xl px-4 py-8 sm:px-6 sm:py-12">
      <Link href="/" className="text-sm underline underline-offset-4">
        Главная
      </Link>
      <h1 className="mb-6 mt-6 text-3xl font-semibold">Профиль</h1>
      <TelegramBindingPanel />
    </main>
  );
}
