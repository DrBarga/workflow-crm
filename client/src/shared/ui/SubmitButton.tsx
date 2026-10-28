import type { ButtonHTMLAttributes, ReactNode } from "react";
import { SpinnerIcon } from "./common/Icons";
import styles from "./common/block.module.css";

interface SubmitButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  /** Подпись на время запроса, например «Перевіряємо…». */
  loadingLabel?: string;
  loading?: boolean;
}

export function SubmitButton({
  children,
  loadingLabel,
  loading = false,
  disabled,
  type = "submit",
  ...button
}: SubmitButtonProps) {
  return (
    <button
      type={type}
      className={`${styles.submit} ${loading ? styles.submitLoading : ""}`}
      disabled={disabled ?? loading}
      {...button}
    >
      {loading ? (
        <>
          <SpinnerIcon className={styles.spinner} />
          {loadingLabel ?? children}
        </>
      ) : (
        children
      )}
    </button>
  );
}
