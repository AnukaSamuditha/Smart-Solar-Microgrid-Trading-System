import { apiClient } from "@/providers/api-client"

// Pending/Rejected only ever occur for a prosumer-submitted request (mobile app self-service) -
// a staff-created reservation (POST /api/v1/reservations below) goes straight to Confirmed.
// Completed is set by the QR transaction finalize flow, never directly from this dashboard.
export type ReservationStatus = "Pending" | "Confirmed" | "Rejected" | "Cancelled" | "Completed"

export interface Reservation {
  id: string
  prosumerNic: string
  prosumerFullName: string | null
  nodeId: string
  nodeName: string | null
  slotId: string
  startTime: string
  endTime: string
  energyAmount: number | null
  status: ReservationStatus
  rejectionReason: string | null
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

// approves a Pending prosumer-submitted request: moves it to Confirmed and marks the node's
// battery slot Reserved, after re-verifying the slot is still free (Backoffice or Grid Operator)
export async function approveReservation(id: string): Promise<Reservation> {
  const { data } = await apiClient.patch<Reservation>(`/api/v1/reservations/${id}/approve`)
  return data
}

export interface RejectReservationPayload {
  id: string
  reason?: string
}

// rejects a Pending prosumer-submitted request; terminal - the prosumer would need to submit a
// new request (Backoffice or Grid Operator)
export async function rejectReservation({ id, reason }: RejectReservationPayload): Promise<void> {
  await apiClient.patch(`/api/v1/reservations/${id}/reject`, { reason })
}
