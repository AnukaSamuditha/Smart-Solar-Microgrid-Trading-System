"use client"

import { useState } from "react"
import { MoreHorizontalIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { Reservation } from "@/lib/api/reservations"

import { CancelReservationDialog } from "./cancel-reservation-dialog"
import { EditReservationDialog } from "./edit-reservation-dialog"

// both actions share the "ReservationManagement" policy (Backoffice + Grid Operator), so no
// role-conditional hiding is needed here, unlike ProsumerRowActions' reactivate-only restriction
export function ReservationRowActions({ reservation }: { reservation: Reservation }) {
  const [editOpen, setEditOpen] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const isCancelled = reservation.status === "Cancelled"

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <MoreHorizontalIcon />
          <span className="sr-only">Reservation actions</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem disabled={isCancelled} onClick={() => setEditOpen(true)}>
            Reschedule
          </DropdownMenuItem>
          <DropdownMenuItem disabled={isCancelled} onClick={() => setCancelOpen(true)}>
            Cancel
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <EditReservationDialog reservation={reservation} open={editOpen} onOpenChange={setEditOpen} />
      <CancelReservationDialog reservation={reservation} open={cancelOpen} onOpenChange={setCancelOpen} />
    </>
  )
}
