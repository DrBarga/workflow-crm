import { QRCodeSVG } from "qrcode.react";
import type { TelegramLink } from "../api/telegramApi";

export function TelegramLinkCard({ link }: { link: TelegramLink }) {
  return (
    <div className="mt-6 rounded-xl border border-gray-200 bg-gray-50 p-5 text-gray-900">
      <h3 className="font-semibold">Подтвердите привязку в Telegram</h3>
      <p className="mt-2 text-sm">
        Откройте бота или отсканируйте QR-код, нажмите «Начать» и подтвердите учётную запись с вашим
        email.
      </p>
      <div className="my-4 flex justify-center">
        <QRCodeSVG
          value={link.link}
          size={192}
          marginSize={4}
          title="Ссылка для привязки Telegram"
        />
      </div>
      <a
        className="inline-block rounded-lg bg-gray-900 px-4 py-3 font-medium text-white"
        href={link.link}
        target="_blank"
        rel="noreferrer"
      >
        Открыть бота
      </a>
      <p className="mt-3 text-sm">
        Ссылка действует до{" "}
        {new Date(link.expiresAt).toLocaleTimeString("ru-RU", {
          hour: "2-digit",
          minute: "2-digit",
        })}
        . После подтверждения статус обновится автоматически.
      </p>
    </div>
  );
}
