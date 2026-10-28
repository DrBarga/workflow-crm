import type { InputHTMLAttributes, ReactNode } from "react";
import { AlertIcon } from "./common/Icons";
import styles from "./common/form.module.css";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: string;
  /** Ссылка справа от подписи, например «Забули пароль?». */
  hint?: ReactNode;
  error?: string;
  /** Подсветить поле без сообщения: текст ошибки показан под формой. */
  invalid?: boolean;
}

export function TextField({ id, label, hint, error, invalid, ...input }: TextFieldProps) {
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
          aria-invalid={error || invalid ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          {...input}
        />
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
