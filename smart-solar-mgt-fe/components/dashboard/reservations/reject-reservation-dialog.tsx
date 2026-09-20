"use client"

import { useState } from "react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useRejectReservation } from "@/hooks/use-reservations"
import { toApiError } from "@/lib/api/errors"
import type { Reservation } from "@/lib/api/reservations"

interface RejectReservationDialogProps {
  reservation: Reservation
  open: boolean
  onOpenChange: (open: boolean) => void
}

// terminal - a rejected request has no path back to Pending; the prosumer would need to submit a
// new request from the mobile app
export function RejectReservationDialog({
  reservation,
  open,
  onOpenChange,
}: RejectReservationDialogProps) {
  const reject = useRejectReservation()
  const [reason, setReason] = useState("")
  const label = reservation.prosumerFullName ?? reservation.prosumerNic

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setReason("")
    }
    onOpenChange(next)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    reject.mutate(
      { id: reservation.id, reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          toast.success(`Reservation rejected for ${label}.`)
          handleOpenChange(false)
        },
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Reject this reservation?</DialogTitle>
            <DialogDescription>
              This is terminal - {label} would need to submit a new request from the mobile app.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field>
              <FieldLabel htmlFor={`reject-reservation-reason-${reservation.id}`}>
                Reason (optional)
              </FieldLabel>
              <Input
                id={`reject-reservation-reason-${reservation.id}`}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Shown to the prosumer"
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" variant="destructive" disabled={reject.isPending}>
              {reject.isPending ? "Rejecting..." : "Reject reservation"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
