"use client"

import { useMemo, useState } from "react"
import Link from "next/link"
import { ChevronRightIcon } from "lucide-react"

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@/components/ui/sidebar"
import { canAccess, isNavGroup, type NavEntry, type NavGroup, type Role } from "@/lib/nav-config"
import { cn } from "@/lib/utils"

// scores how well a nav href matches the current pathname: an exact match always outranks a
// prefix match, so a nested route (e.g. /grid-nodes/schedules) never also lights up a sibling
// nav entry (e.g. /grid-nodes) just because one href happens to be a string-prefix of the other
function matchScore(pathname: string, href: string): number {
  if (pathname === href) return href.length * 2 + 1
  if (href !== "/" && pathname.startsWith(`${href}/`)) return href.length
  return -1
}

function flattenHrefs(entries: NavEntry[]): string[] {
  return entries.flatMap((entry) => (isNavGroup(entry) ? entry.items.map((item) => item.href) : [entry.href]))
}

// the single most specific nav href matching the current pathname (or undefined if none match)
function getActiveHref(pathname: string, entries: NavEntry[]): string | undefined {
  let best: string | undefined
  let bestScore = -1
  for (const href of flattenHrefs(entries)) {
    const score = matchScore(pathname, href)
    if (score > bestScore) {
      bestScore = score
      best = href
    }
  }
  return best
}

// the current-route pill: a visible border plus a touch more vertical padding
// than the resting rows, so the active link reads as a raised card
const activeMain = "border border-sidebar-border h-auto py-2.5"
const activeSub = "border border-sidebar-border h-auto py-2"

function NavGroupItem({ entry, activeHref }: { entry: NavGroup; activeHref: string | undefined }) {
  const containsActive = entry.items.some((item) => item.href === activeHref)
  // uncontrolled `defaultOpen` can't react to route changes without Base UI warning
  // about mutating it post-init, so this stays controlled. State is adjusted during
  // render (React's recommended pattern for state derived from a changing prop)
  // rather than in an effect, and only ever opens the group on entering one of its
  // routes - it never auto-closes it.
  const [open, setOpen] = useState(containsActive)
  const [trackedActive, setTrackedActive] = useState(containsActive)

  if (containsActive !== trackedActive) {
    setTrackedActive(containsActive)
    if (containsActive) {
      setOpen(true)
    }
  }

  return (
    <Collapsible
      open={open}
      onOpenChange={setOpen}
      className="group/collapsible"
      render={<SidebarMenuItem />}
    >
      <CollapsibleTrigger render={<SidebarMenuButton tooltip={entry.title} />}>
        <entry.icon />
        <span>{entry.title}</span>
        <ChevronRightIcon className="ml-auto transition-transform duration-200 group-data-open/collapsible:rotate-90" />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <SidebarMenuSub className="gap-0 border-l-0 px-0">
          {entry.items.map((item, index) => {
            const isActive = item.href === activeHref
            const isLast = index === entry.items.length - 1

            return (
              <SidebarMenuSubItem key={item.href} className="relative pl-3">
                {/* corner: trunk curving right into this route */}
                <span
                  aria-hidden="true"
                  className="absolute top-0 left-0 h-3.5 w-3 rounded-bl-md border-b border-l border-sidebar-border"
                />
                {/* trunk continuing down to the next route, if any */}
                {!isLast ? (
                  <span
                    aria-hidden="true"
                    className="absolute top-3.5 bottom-0 left-0 w-px bg-sidebar-border"
                  />
                ) : null}
                <SidebarMenuSubButton
                  isActive={isActive}
                  render={<Link href={item.href} />}
                  className={cn(isActive && activeSub)}
                >
                  <span>{item.title}</span>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            )
          })}
        </SidebarMenuSub>
      </CollapsibleContent>
    </Collapsible>
  )
}

export function NavMain({
  label,
  entries,
  role,
  pathname,
}: {
  label: string
  entries: NavEntry[]
  role: Role | undefined
  pathname: string
}) {
  const visible = entries.filter((entry) => canAccess(entry.roles, role))
  const activeHref = useMemo(() => getActiveHref(pathname, entries), [pathname, entries])

  if (!visible.length) {
    return null
  }

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{label}</SidebarGroupLabel>
      <SidebarMenu className="gap-2">
        {visible.map((entry) =>
          isNavGroup(entry) ? (
            <NavGroupItem key={entry.title} entry={entry} activeHref={activeHref} />
          ) : (
            <SidebarMenuItem key={entry.href}>
              <SidebarMenuButton
                tooltip={entry.title}
                isActive={entry.href === activeHref}
                render={<Link href={entry.href} />}
                className={cn(entry.href === activeHref && activeMain)}
              >
                <entry.icon />
                <span>{entry.title}</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          )
        )}
      </SidebarMenu>
    </SidebarGroup>
  )
}
