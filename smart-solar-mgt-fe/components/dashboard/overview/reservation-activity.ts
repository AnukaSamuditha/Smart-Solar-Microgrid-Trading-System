// reservation-activity.ts
// Purpose: Shared classification for "today's reservations" (see useTodaysReservations), used by
// both the Active Hub Nodes table's Reservations column and the Hourly Dispatch chart so the two
// widgets agree on what counts as completed/active/scheduled at any given moment.

import type { Reservation } from "@/lib/api/reservations"

export type ReservationActivityBucket = "completed" | "active" | "scheduled"

export function classifyReservation(reservation: Reservation, now: Date): ReservationActivityBucket {
  const start = new Date(reservation.startTime)
  const end = new Date(reservation.endTime)

  if (end <= now) {
    return "completed"
  }
  if (start <= now) {
    return "active"
  }
  return "scheduled"
}
