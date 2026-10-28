"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { IconDots } from "@tabler/icons-react";
import { navItems } from "../model/navigation";
import { MoreSheet } from "./MoreSheet";
import styles from "./MobileNav.module.css";

// Больше пяти вкладок в строку не влезает: четыре раздела и кнопка «Ще».
const MAX_TABS = 5;
const VISIBLE_TABS = 4;

export function MobileNav({ role }: { role: string | undefined }) {
  const pathname = usePathname();
  const [sheetOpen, setSheetOpen] = useState(false);
  const items = navItems(role);

  const overflows = items.length > MAX_TABS;
  const tabs = overflows ? items.slice(0, VISIBLE_TABS) : items;
  const hidden = overflows ? items.slice(VISIBLE_TABS) : [];
  const hiddenActive = hidden.some((item) => pathname.startsWith(item.href));

  if (items.length === 0) return null;

  return (
    <>
      <nav className={styles.nav}>
        {tabs.map(({ section, href, label, Icon }) => (
          <Link
            key={section}
            href={href}
            className={`${styles.tab} ${pathname.startsWith(href) ? styles.tabActive : ""}`}
          >
            <Icon size={20} stroke={2} />
            <span className={styles.tabLabel}>{label}</span>
          </Link>
        ))}
        {overflows ? (
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            aria-expanded={sheetOpen}
            aria-haspopup="dialog"
            className={`${styles.tab} ${hiddenActive ? styles.tabActive : ""}`}
          >
            <IconDots size={20} stroke={2} />
            <span className={styles.tabLabel}>Ще</span>
          </button>
        ) : null}
      </nav>
      {sheetOpen ? (
        <MoreSheet items={hidden} pathname={pathname} onClose={() => setSheetOpen(false)} />
      ) : null}
    </>
  );
}
