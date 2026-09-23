"use client"

import * as React from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"

import { useMe } from "@/hooks/use-me"
import { mainNav } from "@/lib/nav-config"
import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar"

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const pathname = usePathname()
  const { data: user } = useMe()

  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/" />}
              className="hover:bg-transparent active:bg-transparent"
            >
              {/* same mark as the login page's visual panel, in plain brand yellow */}
              <svg
                viewBox="0 0 32 32"
                fill="none"
                className="!size-8 shrink-0 text-primary"
                aria-hidden="true"
              >
                <path
                  d="M17.8 3.5L7.5 17.1H15L13.8 28.5L24.5 14.8H17L17.8 3.5Z"
                  fill="currentColor"
                />
              </svg>
              <span className="truncate text-base font-semibold text-sidebar-foreground">
                SolarSync
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain label="Main Menu" entries={mainNav} role={user?.role} pathname={pathname} />
      </SidebarContent>
      <SidebarFooter>{user ? <NavUser user={user} /> : null}</SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
