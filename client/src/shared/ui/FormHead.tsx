import type { ReactNode } from "react";
import styles from "./common/block.module.css";

interface FormHeadProps {
  title: string;
  description?: ReactNode;
}

export function FormHead({ title, description }: FormHeadProps) {
  return (
    <header className={styles.head}>
      <h1 className={styles.headTitle}>{title}</h1>
      {description ? <p className={styles.headText}>{description}</p> : null}
    </header>
  );
}
