"use client";

import Link from "next/link";
import { useEffect } from "react";
import { createPortal } from "react-dom";
import type { NavItem } from "../model/navigation";
import styles from "./MobileNav.module.css";

interface MoreSheetProps {
  items: NavItem[];
  pathname: string;
  onClose: () => void;
}

// Разделы, не поместившиеся в нижнюю панель. Портал в body нужен,
// чтобы шторка не зависела от контекста наложения внутри страницы.
export function MoreSheet({ items, pathname, onClose }: MoreSheetProps) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };

    document.addEventListener("keydown", closeOnEscape);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.removeEventListener("keydown", closeOnEscape);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  if (typeof document === "undefined") return null;

  return createPortal(
    <div className={styles.overlay}>
      <button type="button" className={styles.backdrop} aria-label="Закрити" onClick={onClose} />
      <div role="dialog" aria-label="Інші розділи" className={styles.sheet}>
        <span className={styles.handle} aria-hidden />
        <nav className={styles.sheetNav}>
          {items.map(({ section, href, label, Icon }) => {
            const active = pathname.startsWith(href);
            return (
              <Link
                key={section}
                href={href}
                onClick={onClose}
                className={`${styles.sheetLink} ${active ? styles.sheetLinkActive : ""}`}
              >
                <Icon size={18} stroke={2} />
                <span className={styles.sheetLabel}>{label}</span>
              </Link>
            );
          })}
        </nav>
      </div>
    </div>,
    document.body,
  );
}
