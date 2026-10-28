import Image from "next/image";
import type { ReactNode } from "react";
import styles from "./AuthLayout.module.css";

// Панель без рекламного текста: систему открывают те, кто в ней работает.
// Один фокус — знак, много воздуха.
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className={styles.layout}>
      <aside className={styles.brand}>
        <div className={styles.brandInner}>
          <Image
            className={styles.brandMark}
            src="/workflow-mark.svg"
            alt=""
            width={438}
            height={488}
            priority
          />
          <div className={styles.brandLockup}>
            <Image
              className={styles.brandLogo}
              src="/workflow-logo.svg"
              alt="WORKFLOW"
              width={1959}
              height={369}
              priority
            />
            <p className={styles.brandModule}>Розвиток мережі</p>
          </div>
        </div>
      </aside>
      <section className={styles.pane}>
        <div className={styles.form}>{children}</div>
      </section>
    </main>
  );
}
