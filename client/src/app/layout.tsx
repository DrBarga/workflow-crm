import type { Metadata } from "next";
import { Commissioner } from "next/font/google";
import "@app/_styles/globals.css";
import { StoreProvider } from "@app/_providers/StoreProvider";

// Шрифт макета. Подключается через next/font: он сам хостит файлы и
// не даёт FOUT, в отличие от ссылки на Google Fonts.
const commissioner = Commissioner({
  variable: "--font-commissioner",
  subsets: ["latin", "cyrillic"],
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: "Workflow CRM",
  description: "CRM and workflow management for business locations, contacts and tasks.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="uk" className={`${commissioner.variable} h-full`}>
      <body className="min-h-full flex flex-col">
        <StoreProvider>{children}</StoreProvider>
      </body>
    </html>
  );
}
