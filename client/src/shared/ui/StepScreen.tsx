import type { ReactNode } from "react";
import styles from "./common/step.module.css";

interface StepScreenProps {
  title: string;
  description: ReactNode;
  children?: ReactNode;
}

// Экран-состояние вместо формы: «Перевірте пошту», «Пароль змінено» и т.п.
// aria-live озвучивает подмену содержимого тем, кто пользуется скринридером.
export function StepScreen({ title, description, children }: StepScreenProps) {
  return (
    <section role="status" aria-live="polite">
      <h1 className={styles.stepTitle}>{title}</h1>
      <p className={styles.stepText}>{description}</p>
      {children}
    </section>
  );
}

export function StepBox({ children }: { children: ReactNode }) {
  return <div className={styles.box}>{children}</div>;
}

export function StepLinks({ children }: { children: ReactNode }) {
  return <div className={styles.links}>{children}</div>;
}
