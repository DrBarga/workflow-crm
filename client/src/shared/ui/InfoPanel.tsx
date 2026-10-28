import type { ReactNode } from "react";
import { AlertIcon, ClockIcon } from "./common/Icons";
import styles from "./common/step.module.css";

type PanelVariant = "bad" | "warn" | "wait";

interface InfoPanelProps {
  variant: PanelVariant;
  children: ReactNode;
  /** Правая колонка варианта wait: обратный отсчёт. */
  countdown?: string;
}

const VARIANT_CLASS: Record<PanelVariant, string> = {
  bad: styles.panelBad,
  warn: styles.panelWarn,
  wait: styles.panelWait,
};

// Плашка последствий: почему состояние наступило и что с ним делать.
export function InfoPanel({ variant, children, countdown }: InfoPanelProps) {
  return (
    <p className={`${styles.panel} ${VARIANT_CLASS[variant]}`}>
      {variant === "wait" ? (
        <ClockIcon className={styles.panelIcon} />
      ) : (
        <AlertIcon size={14} className={styles.panelIcon} />
      )}
      <span>{children}</span>
      {countdown ? <b>{countdown}</b> : null}
    </p>
  );
}
