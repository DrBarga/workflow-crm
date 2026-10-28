import Link from "next/link";
import type { Route } from "next";
import styles from "./common/block.module.css";

interface BackLinkProps<T extends string> {
  href: Route<T>;
  children: string;
}

export function BackLink<T extends string>({ href, children }: BackLinkProps<T>) {
  return (
    <Link className={styles.back} href={href}>
      {children}
    </Link>
  );
}
