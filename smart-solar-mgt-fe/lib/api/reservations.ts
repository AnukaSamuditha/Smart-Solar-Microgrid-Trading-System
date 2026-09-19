import { apiClient } from "@/providers/api-client"

export type ReservationStatus = "Confirmed" | "Cancelled"

export interface Reservation {
  id: string
  prosumerNic: string
  prosumerFullName: string | null
  nodeId: string
  nodeName: string | null
  slotId: string
  startTime: string
  endTime: string
  status: ReservationStatus
  createdAt: string
  updatedAt: string | null
}

export interface PagedResult<T> {
  items: T[]
  totalCount: number
  page: number
  pageSize: number
}

export interface ListReservationsParams {
  prosumerNic?: string
  nodeId?: string
  status?: ReservationStatus
  dateFrom?: string
  dateTo?: string
  page?: number
  pageSize?: number
  sortBy?: "StartTime" | "CreatedAt"
  sortDir?: "asc" | "desc"
}

// backend-driven filter/sort/pagination (Backoffice or Grid Operator)
export async function listReservations(
  params: ListReservationsParams
): Promise<PagedResult<Reservation>> {
  const { data } = await apiClient.get<PagedResult<Reservation>>("/api/v1/reservations", {
    params,
  })
  return data
}

// fetch a single reservation (Backoffice or Grid Operator)
export async function getReservation(id: string): Promise<Reservation> {
  const { data } = await apiClient.get<Reservation>(`/api/v1/reservations/${id}`)
  return data
}

export interface CreateReservationPayload {
  prosumerNic: string
  nodeId: string
  slotId: string
  startTime: string
  endTime: string
}

// books a slot for a prosumer (staff-assisted — there's no prosumer login on this dashboard);
// startTime must be in the future and no more than 7 days out (Backoffice or Grid Operator)
export async function createReservation(payload: CreateReservationPayload): Promise<Reservation> {
  const { data } = await apiClient.post<Reservation>("/api/v1/reservations", payload)
  return data
}

export interface UpdateReservationPayload {
  id: string
  startTime: string
  endTime: string
}

// reschedules a reservation's time window; node/slot stay fixed. Requires at least 12 hours'
// notice against the reservation's current start time (Backoffice or Grid Operator)
export async function updateReservation({
  id,
  ...body
}: UpdateReservationPayload): Promise<Reservation> {
  const { data } = await apiClient.patch<Reservation>(`/api/v1/reservations/${id}`, body)
  return data
}

// cancels a reservation; requires at least 12 hours' notice against its current start time
// (Backoffice or Grid Operator)
export async function cancelReservation(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/reservations/${id}/cancel`)
}
