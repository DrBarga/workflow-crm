"use client";

import { useState, type FormEvent } from "react";
import { StepLinks, StepScreen, SubmitButton, TextField } from "@shared/ui";
import step from "@shared/ui/common/step.module.css";
import styles from "./common/confirmation.module.css";

interface EmailCodeConfirmationProps {
  email: string;
  expiresIn: string;
  resendIn?: number;
  /** Ошибка проверки кода от backend. */
  error?: string;
  /** Попытки кончились — поле и кнопка блокируются. */
  blocked?: boolean;
  onSubmit?: (code: string) => void;
  /** Повторной отправки кода на почту в API нет — кнопку не показываем. */
  onResend?: () => void;
}

export function EmailCodeConfirmation({
  email,
  expiresIn,
  resendIn = 0,
  error,
  blocked = false,
  onSubmit,
  onResend,
}: EmailCodeConfirmationProps) {
  const [code, setCode] = useState("");

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (code.length === 6) onSubmit?.(code);
  }

  return (
    <StepScreen
      title="Введіть код із пошти"
      description={
        <>
          Надіслали 6-значний код на <b>{email}</b>. Він діє 5 хвилин.
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className={styles.codeField}>
        <TextField
          id="otp"
          label={`Код підтвердження · діє ${expiresIn}`}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="000000"
          value={code}
          disabled={blocked}
          error={error}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
        />
        <SubmitButton disabled={blocked || code.length < 6}>Підтвердити вхід</SubmitButton>
      </form>
      {onResend ? (
        <StepLinks>
          <button
            type="button"
            className={resendIn > 0 ? step.linkMuted : step.linkPrimary}
            disabled={resendIn > 0}
            onClick={onResend}
          >
            {resendIn > 0 ? `Надіслати ще раз можна через ${resendIn} с` : "Надіслати новий код"}
          </button>
        </StepLinks>
      ) : null}
    </StepScreen>
  );
}
