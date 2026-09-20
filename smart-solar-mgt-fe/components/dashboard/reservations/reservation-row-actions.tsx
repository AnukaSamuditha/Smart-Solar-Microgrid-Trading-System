"use client"

import { useState } from "react"
import { MoreHorizontalIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { Reservation } from "@/lib/api/reservations"

import { ApproveReservationDialog } from "./approve-reservation-dialog"
import { CancelReservationDialog } from "./cancel-reservation-dialog"
import { EditReservationDialog } from "./edit-reservation-dialog"
import { RejectReservationDialog } from "./reject-reservation-dialog"

// approve/reject/reschedule/cancel all share the "ReservationManagement" policy (Backoffice +
// Grid Operator), so no role-conditional hiding is needed here, unlike ProsumerRowActions'
// reactivate-only restriction
export function ReservationRowActions({ reservation }: { reservation: Reservation }) {
  const [editOpen, setEditOpen] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)

  const isPending = reservation.status === "Pending"
  // a terminal status can no longer be rescheduled/cancelled - previously this only checked
  // Cancelled, back when Confirmed/Cancelled were the only two statuses that existed
  const isTerminal =
    reservation.status === "Cancelled" ||
    reservation.status === "Rejected" ||
    reservation.status === "Completed"

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <MoreHorizontalIcon />
          <span className="sr-only">Reservation actions</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {isPending ? (
            <>
              <DropdownMenuItem onClick={() => setApproveOpen(true)}>Approve</DropdownMenuItem>
              <DropdownMenuItem variant="destructive" onClick={() => setRejectOpen(true)}>
                Reject
              </DropdownMenuItem>
              <DropdownMenuSeparator />
            </>
          ) : null}
          <DropdownMenuItem disabled={isTerminal} onClick={() => setEditOpen(true)}>
            Reschedule
          </DropdownMenuItem>
          <DropdownMenuItem disabled={isTerminal} onClick={() => setCancelOpen(true)}>
            Cancel
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <ApproveReservationDialog reservation={reservation} open={approveOpen} onOpenChange={setApproveOpen} />
      <RejectReservationDialog reservation={reservation} open={rejectOpen} onOpenChange={setRejectOpen} />
      <EditReservationDialog reservation={reservation} open={editOpen} onOpenChange={setEditOpen} />
      <CancelReservationDialog reservation={reservation} open={cancelOpen} onOpenChange={setCancelOpen} />
    </>
  )
}
