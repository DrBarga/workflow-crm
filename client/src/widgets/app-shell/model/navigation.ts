import {
  IconBuildingStore,
  IconChartBar,
  IconLayoutDashboard,
  IconListCheck,
  IconSettings,
  IconThumbUp,
  IconUsers,
  type Icon,
} from "@tabler/icons-react";

export type NavSection =
  "dashboard" | "objects" | "tasks" | "contacts" | "approvals" | "analytics" | "admin";

export interface NavItem {
  section: NavSection;
  href: string;
  label: string;
  Icon: Icon;
}

const ITEMS: Record<NavSection, Omit<NavItem, "section">> = {
  dashboard: { href: "/dashboard", label: "Дашборд", Icon: IconLayoutDashboard },
  objects: { href: "/objects", label: "Обʼєкти", Icon: IconBuildingStore },
  tasks: { href: "/tasks", label: "Задачі", Icon: IconListCheck },
  contacts: { href: "/contacts", label: "Контакти", Icon: IconUsers },
  approvals: { href: "/approvals", label: "Погодження", Icon: IconThumbUp },
  analytics: { href: "/analytics", label: "Аналітика", Icon: IconChartBar },
  admin: { href: "/admin", label: "Адміністрування", Icon: IconSettings },
};

/** Группы разделов: между ними в рельсе рисуется разделитель. */
export const NAV_GROUPS: readonly NavSection[][] = [
  ["dashboard", "objects", "tasks", "contacts"],
  ["approvals", "analytics"],
  ["admin"],
];

// Видимость разделов по ролям — из макета workflow-app.html. Это только
// подсказка интерфейса: доступ к данным backend проверяет сам.
const SECTIONS_BY_ROLE: Record<string, readonly NavSection[]> = {
  MANAGER: ["dashboard", "objects", "tasks", "contacts", "approvals"],
  REGIONAL_MANAGER: ["dashboard", "objects", "tasks", "contacts", "approvals", "analytics"],
  DEVELOPMENT_DIRECTOR: ["dashboard", "objects", "tasks", "contacts", "approvals", "analytics"],
  CEO: ["dashboard", "objects", "tasks", "analytics"],
  CONTRACT_MANAGER: ["dashboard", "objects", "tasks", "contacts"],
  SUPERADMIN: ["dashboard", "objects", "tasks", "contacts", "approvals", "analytics", "admin"],
};

export function navItem(section: NavSection): NavItem {
  return { section, ...ITEMS[section] };
}

/** Пока роль неизвестна, меню пустое: мигать лишними пунктами хуже. */
export function visibleSections(role: string | undefined): readonly NavSection[] {
  if (!role) return [];

  return SECTIONS_BY_ROLE[role] ?? [];
}

export function navGroups(role: string | undefined): NavItem[][] {
  const visible = new Set(visibleSections(role));

  return NAV_GROUPS.map((group) =>
    group.filter((section) => visible.has(section)).map(navItem),
  ).filter((group) => group.length > 0);
}

export function navItems(role: string | undefined): NavItem[] {
  return visibleSections(role).map(navItem);
}
