"use client";

import { useState } from "react";
import { useTelegramBinding } from "../model/useTelegramBinding";
import { TelegramLinkCard } from "./TelegramLinkCard";

const button = "rounded-lg border border-gray-300 px-4 py-3 font-medium disabled:opacity-50";
export function TelegramBindingPanel() {
  const binding = useTelegramBinding();
  const [unlinkUser, setUnlinkUser] = useState<string | null>(null);
  const confirmUnlink = binding.profile !== null && unlinkUser === binding.profile.id;
  return (
    <section
      className="rounded-2xl border border-gray-200 bg-white p-6 text-gray-900 shadow-sm"
      aria-labelledby="telegram-heading"
    >
      <h2 id="telegram-heading" className="text-xl font-semibold">
        Telegram
      </h2>
      {binding.loading && (
        <p className="mt-4" role="status">
          Загрузка профиля...
        </p>
      )}
      {binding.profile && (
        <>
          <p className="mt-2 break-all text-sm text-gray-600">{binding.profile.email}</p>
          <p className="mt-4 font-medium" role="status">
            {binding.profile.telegram.linked ? "Telegram подключён" : "Telegram не подключён"}
          </p>
          <p className="mt-2 text-sm text-gray-600">
            Подключите Telegram для уведомлений и подтверждения входа.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <button
              className={button}
              disabled={binding.busy || confirmUnlink}
              onClick={() => void binding.generateLink()}
            >
              {binding.busy
                ? "Подождите..."
                : binding.link
                  ? "Получить новую ссылку"
                  : binding.profile.telegram.linked
                    ? "Заменить Telegram"
                    : "Подключить Telegram"}
            </button>
            {binding.profile.telegram.linked && !confirmUnlink && (
              <button
                className={button}
                disabled={binding.busy}
                onClick={() => setUnlinkUser(binding.profile?.id ?? null)}
              >
                Отключить Telegram
              </button>
            )}
          </div>
          {confirmUnlink && (
            <div className="mt-5 rounded-lg border border-gray-300 p-4">
              <p>Отключить Telegram? После этого потребуется повторный вход в систему.</p>
              <div className="mt-3 flex flex-wrap gap-3">
                <button
                  className={button}
                  disabled={binding.busy}
                  onClick={() => void binding.unlink()}
                >
                  Подтвердить отключение
                </button>
                <button
                  className={button}
                  disabled={binding.busy}
                  onClick={() => setUnlinkUser(null)}
                >
                  Отмена
                </button>
              </div>
            </div>
          )}
          {binding.link && <TelegramLinkCard link={binding.link} />}
        </>
      )}
      {binding.notice && (
        <p className="mt-4" role="status">
          {binding.notice}
        </p>
      )}
      {binding.error && (
        <p className="mt-4 text-red-700" role="alert">
          {binding.error}
        </p>
      )}
    </section>
  );
}
