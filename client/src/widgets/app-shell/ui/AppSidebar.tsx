"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { IconLogout } from "@tabler/icons-react";
import { navGroups, type NavItem } from "../model/navigation";
import styles from "./AppSidebar.module.css";

interface AppSidebarProps {
  role: string | undefined;
  onLogout: () => void;
}

export function AppSidebar({ role, onLogout }: AppSidebarProps) {
  const pathname = usePathname();
  const groups = navGroups(role);

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>
        <Link href="/" className={styles.logoMark} aria-label="WORKFLOW · Розвиток мережі">
          <Image src="/favicon.svg" alt="" width={24} height={24} priority />
        </Link>
      </div>

      <nav className={styles.nav}>
        {groups.map((group, index) => (
          <div key={group[0].section} className={styles.group}>
            {index > 0 ? <span className={styles.divider} aria-hidden /> : null}
            {group.map((item) => (
              <SidebarItem key={item.section} item={item} active={pathname.startsWith(item.href)} />
            ))}
          </div>
        ))}
      </nav>

      <div className={styles.footer}>
        <button type="button" className={styles.footerButton} aria-label="Вийти" onClick={onLogout}>
          <IconLogout size={21} stroke={1.7} />
          <span role="tooltip" className={styles.tooltip}>
            Вийти
          </span>
        </button>
      </div>
    </aside>
  );
}

function SidebarItem({ item, active }: { item: NavItem; active: boolean }) {
  const { href, label, Icon } = item;

  return (
    <div className={styles.item}>
      {active ? (
        <>
          <span className={`${styles.notch} ${styles.notchTop}`} aria-hidden>
            <span />
          </span>
          <span className={`${styles.notch} ${styles.notchBottom}`} aria-hidden>
            <span />
          </span>
        </>
      ) : null}
      <Link
        href={href}
        aria-current={active ? "page" : undefined}
        aria-label={label}
        className={`${styles.link} ${active ? styles.linkActive : ""}`}
      >
        <span className={`${styles.icon} ${active ? styles.iconActive : ""}`}>
          <Icon size={21} stroke={active ? 2 : 1.7} />
        </span>
        <span role="tooltip" className={styles.tooltip}>
          {label}
        </span>
      </Link>
    </div>
  );
}
