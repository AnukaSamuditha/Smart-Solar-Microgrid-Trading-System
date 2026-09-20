"use client"

import { toast } from "sonner"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { useApproveReservation } from "@/hooks/use-reservations"
import { toApiError } from "@/lib/api/errors"
import type { Reservation } from "@/lib/api/reservations"

interface ApproveReservationDialogProps {
  reservation: Reservation
  open: boolean
  onOpenChange: (open: boolean) => void
}

// approving re-verifies the slot is still free before confirming (another request against the
// same slot may have been approved in the meantime) - a SlotNoLongerAvailable rejection surfaces
// via toApiError same as any other backend error
export function ApproveReservationDialog({
  reservation,
  open,
  onOpenChange,
}: ApproveReservationDialogProps) {
  const approve = useApproveReservation()
  const label = reservation.prosumerFullName ?? reservation.prosumerNic

  const handleConfirm = () => {
    approve.mutate(reservation.id, {
      onSuccess: () => {
        toast.success(`Reservation approved for ${label}.`)
        onOpenChange(false)
      },
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Approve this reservation?</AlertDialogTitle>
          <AlertDialogDescription>
            The slot will be marked Reserved for {label} once approved.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">Prosumer</dt>
          <dd>{label}</dd>
          <dt className="text-muted-foreground">Grid node</dt>
          <dd>{reservation.nodeName ?? reservation.nodeId}</dd>
          <dt className="text-muted-foreground">Slot</dt>
          <dd>Slot {reservation.slotId}</dd>
          <dt className="text-muted-foreground">Start</dt>
          <dd>{new Date(reservation.startTime).toLocaleString()}</dd>
        </dl>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirm} disabled={approve.isPending}>
            {approve.isPending ? "Approving..." : "Approve"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
