import { apiClient } from "@/providers/api-client"

export interface DashboardNodeSummary {
  activeCount: number
  totalCount: number
  totalCapacityKw: number
}

export interface DashboardBatterySlotSummary {
  available: number
  reserved: number
  occupied: number
  total: number
}

// today's reservation dispatch activity, bucketed by StartTime against the current instant
export interface DashboardReservationSummary {
  totalToday: number
  activeNowCount: number
  scheduledTodayCount: number
  peakHour: number | null
}

export interface DashboardProsumerSummary {
  invited: number
  active: number
  deactivated: number
}

export interface DashboardSummary {
  nodes: DashboardNodeSummary
  batterySlots: DashboardBatterySlotSummary
  reservations: DashboardReservationSummary
  prosumers: DashboardProsumerSummary
}

// platform-status snapshot for the dashboard's KPI strip and battery-slot distribution widget
// (Backoffice or Grid Operator)
export async function getDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await apiClient.get<DashboardSummary>("/api/v1/dashboard/summary")
  return data
}

export type DashboardActivityType = "NodeCreated" | "ProsumerCreated" | "ReservationCreated"

export interface DashboardActivityItem {
  type: DashboardActivityType
  label: string
  createdAt: string
}

export interface DashboardRecentActivity {
  items: DashboardActivityItem[]
}

// merged, most-recent-first feed of node/prosumer/reservation creation events (Backoffice or Grid Operator)
export async function getRecentActivity(limit = 10): Promise<DashboardRecentActivity> {
  const { data } = await apiClient.get<DashboardRecentActivity>(
    "/api/v1/dashboard/recent-activity",
    { params: { limit } }
  )
  return data
}
