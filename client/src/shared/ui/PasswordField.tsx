"use client";

import { useState, type InputHTMLAttributes, type ReactNode } from "react";
import { AlertIcon, EyeIcon, EyeOffIcon } from "./common/Icons";
import styles from "./common/form.module.css";

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string;
  /** Подсветить поле без сообщения: текст ошибки показан под формой. */
  invalid?: boolean;
  /** Глаз показывают только у полей ввода пароля, у поля повтора его нет. */
  revealable?: boolean;
}

export function PasswordField({
  id,
  label,
  hint,
  error,
  invalid,
  revealable = true,
  ...input
}: PasswordFieldProps) {
  const [revealed, setRevealed] = useState(false);
  const errorId = `${id}-error`;

  return (
    <div className={styles.field}>
      <div className={styles.fieldTop}>
        <label className={styles.label} htmlFor={id}>
          {label}
        </label>
        {hint}
      </div>
      <div className={`${styles.control} ${error || invalid ? styles.controlInvalid : ""}`}>
        <input
          id={id}
          className={styles.input}
          type={revealed ? "text" : "password"}
          aria-invalid={error || invalid ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          {...input}
        />
        {revealable ? (
          <button
            type="button"
            className={styles.controlButton}
            aria-label={revealed ? "Сховати пароль" : "Показати пароль"}
            aria-pressed={revealed}
            onClick={() => setRevealed((shown) => !shown)}
          >
            {revealed ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        ) : null}
      </div>
      {error ? (
        <p className={styles.error} id={errorId}>
          <AlertIcon size={13} />
          {error}
        </p>
      ) : null}
    </div>
  );
}
