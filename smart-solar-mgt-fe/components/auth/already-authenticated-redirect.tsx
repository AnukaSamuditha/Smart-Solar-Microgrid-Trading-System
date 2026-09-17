"use client"

import { useEffect } from "react"
import { useRouter } from "next/navigation"

import { useMe } from "@/hooks/use-me"

// sends an already-authenticated visitor away from the login page instead of showing it again
export function AlreadyAuthenticatedRedirect() {
  const router = useRouter()
  const { data: user } = useMe()

  useEffect(() => {
    if (user) {
      router.replace("/")
    }
  }, [user, router])

  return null
}
