import type { ReactNode } from "react";
import { ShieldIcon } from "./common/Icons";
import styles from "./common/block.module.css";

interface SecureNoteProps {
  children: ReactNode;
  /** Часть сносок в макете идёт без иконки — только текст. */
  withIcon?: boolean;
}

export function SecureNote({ children, withIcon = true }: SecureNoteProps) {
  return (
    <p className={styles.secure}>
      {withIcon ? <ShieldIcon className={styles.secureIcon} /> : null}
      <span>{children}</span>
    </p>
  );
}
