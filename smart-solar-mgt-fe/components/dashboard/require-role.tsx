"use client"

import { useEffect } from "react"
import { useRouter } from "next/navigation"

import { useMe } from "@/hooks/use-me"
import type { Role } from "@/lib/nav-config"

// route-level backstop for admin-only pages: hiding the nav item isn't access control,
// so a Grid Operator hitting the URL directly still gets bounced to the dashboard home
export function RequireRole({ roles, children }: { roles: Role[]; children: React.ReactNode }) {
  const router = useRouter()
  const { data: user, isPending } = useMe()
  const allowed = !!user && roles.includes(user.role)

  useEffect(() => {
    if (!isPending && user && !allowed) {
      router.replace("/")
    }
  }, [isPending, user, allowed, router])

  if (!allowed) {
    return null
  }

  return <>{children}</>
}
