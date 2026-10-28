import type { ReactNode } from "react";
import { AlertIcon } from "./common/Icons";
import styles from "./common/block.module.css";

// Ошибка всей формы: неверная пара пошта/пароль, истёкшая ссылка и т.п.
export function FormAlert({ children }: { children: ReactNode }) {
  return (
    <div className={styles.alert} role="alert">
      <AlertIcon className={styles.alertIcon} />
      <span>{children}</span>
    </div>
  );
}
