"use client"

import { useEffect } from "react"
import { useRouter } from "next/navigation"

import { useMe } from "@/hooks/use-me"

// keeps the whole dashboard shell behind auth; useMe() is the only source of truth for
// session state (see CLAUDE.md: no Next.js middleware, cookies live on the API's origin)
export function DashboardAuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const { data: user, isPending, isError } = useMe()

  useEffect(() => {
    if (isError) {
      router.replace("/login")
    }
  }, [isError, router])

  if (isPending || isError || !user) {
    return (
      <div className="flex min-h-svh w-full items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-primary" />
      </div>
    )
  }

  return <>{children}</>
}
