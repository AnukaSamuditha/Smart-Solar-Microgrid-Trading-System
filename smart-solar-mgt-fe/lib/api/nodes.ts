import { apiClient } from "@/providers/api-client"

export type MicrogridNodeStatus = "Active" | "Deactivated"
export type BatterySlotStatus = "Available" | "Reserved" | "Occupied"

export type DayOfWeek =
  | "Sunday"
  | "Monday"
  | "Tuesday"
  | "Wednesday"
  | "Thursday"
  | "Friday"
  | "Saturday"

export interface BatterySlot {
  slotId: string
  status: BatterySlotStatus
}

// openTime/closeTime are "HH:mm:ss" strings (the API's TimeOnly format), null when isClosed
export interface ScheduleEntry {
  dayOfWeek: DayOfWeek
  openTime: string | null
  closeTime: string | null
  isClosed: boolean
}

export interface MicrogridNode {
  id: string
  name: string
  latitude: number
  longitude: number
  capacityKw: number
  batterySlots: BatterySlot[]
  schedule: ScheduleEntry[]
  status: MicrogridNodeStatus
  createdAt: string
  updatedAt: string | null
}

export interface PagedResult<T> {
  items: T[]
  totalCount: number
  page: number
  pageSize: number
}

export interface ListNodesParams {
  search?: string
  status?: MicrogridNodeStatus
  page?: number
  pageSize?: number
  sortBy?: "Name" | "CapacityKw" | "CreatedAt"
  sortDir?: "asc" | "desc"
}

// backend-driven search/status filter/sort/pagination (Backoffice or Grid Operator)
export async function listNodes(params: ListNodesParams): Promise<PagedResult<MicrogridNode>> {
  const { data } = await apiClient.get<PagedResult<MicrogridNode>>("/api/v1/nodes", { params })
  return data
}

// fetch a single node (Backoffice or Grid Operator)
export async function getNode(id: string): Promise<MicrogridNode> {
  const { data } = await apiClient.get<MicrogridNode>(`/api/v1/nodes/${id}`)
  return data
}

export interface CreateNodePayload {
  name: string
  latitude: number
  longitude: number
  capacityKw: number
  batterySlotCount: number
  // "HH:mm:ss" (the API's TimeOnly format); seeds the same daily window on every day of the
  // node's initial weekly schedule
  operatingStartTime: string
  operatingEndTime: string
}

// registers a new solar grid hub with GPS location, capacity, a generated battery slot list, and
// an initial daily operating window applied to every day of the week (Backoffice-only)
export async function createNode(payload: CreateNodePayload): Promise<MicrogridNode> {
  const { data } = await apiClient.post<MicrogridNode>("/api/v1/nodes", payload)
  return data
}

export interface UpdateNodeSchedulePayload {
  id: string
  schedule: ScheduleEntry[]
}

// replaces a node's entire weekly operating-hours schedule (Backoffice-only)
export async function updateNodeSchedule({
  id,
  schedule,
}: UpdateNodeSchedulePayload): Promise<MicrogridNode> {
  const { data } = await apiClient.patch<MicrogridNode>(`/api/v1/nodes/${id}/schedule`, {
    schedule,
  })
  return data
}

export interface UpdateBatterySlotStatusPayload {
  id: string
  slotId: string
  status: BatterySlotStatus
}

// updates a single battery slot's status (Backoffice or Grid Operator, per
// project-specification.md section 6)
export async function updateBatterySlotStatus({
  id,
  slotId,
  status,
}: UpdateBatterySlotStatusPayload): Promise<MicrogridNode> {
  const { data } = await apiClient.patch<MicrogridNode>(
    `/api/v1/nodes/${id}/battery-slots/${slotId}`,
    { status }
  )
  return data
}

// blocks a node without deleting it; the backend refuses with 409 ActiveReservationsExist while
// active energy reservations exist against it (Backoffice-only)
export async function deactivateNode(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/nodes/${id}/deactivate`)
}

// restores a deactivated node (Backoffice-only)
export async function reactivateNode(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/nodes/${id}/reactivate`)
}
