"use client";

import { StepLinks, StepScreen } from "@shared/ui";
import { DeviceIcon, SpinnerIcon } from "@shared/ui/common/Icons";
import block from "@shared/ui/common/block.module.css";
import step from "@shared/ui/common/step.module.css";
import styles from "./common/confirmation.module.css";

interface TelegramConfirmationProps {
  email: string;
  /** Остаток жизни запроса, «04:37». Приходит из challenge на backend. */
  expiresIn: string;
  /** Описание устройства — то же, что бот показывает в сообщении. */
  device?: string;
  /** Пауза до повторной отправки в секундах, 0 — можно отправлять. */
  resendIn?: number;
  attempt?: { current: number; total: number };
  onResend?: () => void;
}

export function TelegramConfirmation({
  email,
  expiresIn,
  device,
  resendIn = 0,
  attempt,
  onResend,
}: TelegramConfirmationProps) {
  return (
    <StepScreen
      title="Підтвердіть вхід у Telegram"
      description={
        <>
          Ми впізнали новий пристрій і надіслали запит боту WORKFLOW. Підтвердіть вхід для{" "}
          <b>{email}</b>.
        </>
      }
    >
      <div className={styles.wait}>
        <SpinnerIcon className={block.spinner} />
        <span>Очікуємо підтвердження…</span>
        <span className={styles.timer}>{expiresIn}</span>
      </div>
      {device ? (
        <p className={styles.device}>
          <DeviceIcon />
          {device}
        </p>
      ) : null}
      {/* Переключения на почту нет: канал выбирает backend при логине. */}
      {onResend ? (
        <StepLinks>
          <button
            type="button"
            className={resendIn > 0 ? step.linkMuted : step.linkPrimary}
            disabled={resendIn > 0}
            onClick={onResend}
          >
            {resendIn > 0 ? `Надіслати ще раз можна через ${resendIn} с` : "Надіслати ще раз"}
          </button>
        </StepLinks>
      ) : null}
      {attempt ? (
        <p className={styles.attempts}>
          Спроба {attempt.current} з {attempt.total}
        </p>
      ) : null}
    </StepScreen>
  );
}
