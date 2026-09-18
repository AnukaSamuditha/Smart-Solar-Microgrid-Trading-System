import {
  BatteryChargingIcon,
  CalendarClockIcon,
  CalendarRangeIcon,
  HomeIcon,
  MapPinIcon,
  RadioIcon,
  SettingsIcon,
  ShieldUserIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react"

import type { CurrentUser } from "@/lib/api/users"

export type Role = CurrentUser["role"]

export interface NavLink {
  title: string
  href: string
  icon: LucideIcon
  /** Roles allowed to see this entry. Omit to allow every role. */
  roles?: Role[]
}

export interface NavGroup {
  title: string
  icon: LucideIcon
  roles?: Role[]
  items: NavLink[]
}

export type NavEntry = NavLink | NavGroup

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return "items" in entry
}

export function canAccess(roles: Role[] | undefined, role: Role | undefined): boolean {
  if (!roles) return true
  if (!role) return false
  return roles.includes(role)
}

// Backoffice manages grid node registration/schedules and web app user accounts (see
// project-specification.md sections 3.1, 3.3). Prosumer management is shared by both roles,
// except reactivating a deactivated prosumer, which stays Backoffice-only (section 3.2) and is
// enforced in the UI by ProsumerRowActions, not by hiding this nav entry. Grid Operators
// otherwise get operational tools only: reservations and battery slots.
export const mainNav: NavEntry[] = [
  { title: "Dashboard", href: "/", icon: HomeIcon },
  {
    title: "Prosumers",
    href: "/prosumers",
    icon: UsersIcon,
    roles: ["Backoffice", "GridOperator"],
  },
  { title: "Reservations", href: "/reservations", icon: CalendarClockIcon },
  {
    title: "Grid Nodes",
    icon: RadioIcon,
    roles: ["Backoffice"],
    items: [
      { title: "All Nodes", href: "/grid-nodes", icon: MapPinIcon, roles: ["Backoffice"] },
      {
        title: "Node Schedules",
        href: "/grid-nodes/schedules",
        icon: CalendarRangeIcon,
        roles: ["Backoffice"],
      },
    ],
  },
  { title: "Battery Slots", href: "/battery-slots", icon: BatteryChargingIcon },
  { title: "User Management", href: "/users", icon: ShieldUserIcon, roles: ["Backoffice"] },
  { title: "Settings", href: "/settings", icon: SettingsIcon },
]

function flattenLinks(entries: NavEntry[]): NavLink[] {
  return entries.flatMap((entry) => (isNavGroup(entry) ? entry.items : [entry]))
}

const allLinks = flattenLinks(mainNav)

export function getPageTitle(pathname: string): string {
  const match = allLinks.find((link) => link.href === pathname)
  return match?.title ?? "Dashboard"
}

const ROLE_LABELS: Record<Role, string> = {
  Backoffice: "Backoffice",
  GridOperator: "Grid Operator",
}

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role]
}
