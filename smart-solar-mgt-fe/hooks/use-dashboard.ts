import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"

import { useReservations } from "@/hooks/use-reservations"
import { getDashboardSummary, getRecentActivity } from "@/lib/api/dashboard"

// this is a "current status" landing page, not a live telemetry feed (no websockets/SSE exist
// anywhere in this codebase) — a one-minute refetch interval keeps it reasonably current without
// building any push infrastructure
const REFRESH_MS = 60_000

export function useDashboardSummary() {
  return useQuery({
    queryKey: ["dashboard", "summary"],
    queryFn: getDashboardSummary,
    staleTime: REFRESH_MS,
    refetchInterval: REFRESH_MS,
  })
}

export function useRecentActivity(limit = 10) {
  return useQuery({
    queryKey: ["dashboard", "activity", limit],
    queryFn: () => getRecentActivity(limit),
    staleTime: REFRESH_MS,
    refetchInterval: REFRESH_MS,
  })
}

// today's Confirmed reservations (by StartTime), shared by the Active Hub Nodes table's
// Reservations column and the Hourly Dispatch chart — both need the raw per-reservation
// start/end times, unlike the KPI strip's "Today's Dispatch" tile, which reads the cheaper
// pre-aggregated counts from useDashboardSummary() instead
export function useTodaysReservations() {
  const { dateFrom, dateTo } = useMemo(() => {
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    const end = new Date()
    end.setHours(23, 59, 59, 999)
    return { dateFrom: start.toISOString(), dateTo: end.toISOString() }
  }, [])

  return useReservations({
    status: "Confirmed",
    dateFrom,
    dateTo,
    sortBy: "StartTime",
    sortDir: "asc",
    pageSize: 100,
  })
}
