"use client"

import { useState } from "react"
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
import { useCancelReservation } from "@/hooks/use-reservations"
import { toApiError } from "@/lib/api/errors"
import type { Reservation } from "@/lib/api/reservations"

interface CancelReservationDialogProps {
  reservation: Reservation
  open: boolean
  onOpenChange: (open: boolean) => void
}

// blocked (409 InsufficientNotice / AlreadyStarted) once a reservation is within 12 hours of its
// start time, or has already started — shown inline here rather than only as a toast, since
// both are expected, actionable outcomes (see ReservationService.CheckModifiable)
export function CancelReservationDialog({ reservation, open, onOpenChange }: CancelReservationDialogProps) {
  const [blockedMessage, setBlockedMessage] = useState<string | null>(null)
  const cancelReservation = useCancelReservation()
  const label = reservation.prosumerFullName ?? reservation.prosumerNic

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setBlockedMessage(null)
    }
    onOpenChange(next)
  }

  const handleConfirm = () => {
    setBlockedMessage(null)
    cancelReservation.mutate(reservation.id, {
      onSuccess: () => {
        toast.success(`Reservation for ${label} has been cancelled.`)
        handleOpenChange(false)
      },
      onError: (error) => {
        const apiError = toApiError(error)
        if (apiError.code === "InsufficientNotice" || apiError.code === "AlreadyStarted") {
          setBlockedMessage(apiError.message)
          return
        }
        toast.error(apiError.message)
      },
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Cancel reservation for {label}?</AlertDialogTitle>
          <AlertDialogDescription>
            The battery slot will be released and made available for new bookings. This can&apos;t
            be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {blockedMessage ? (
          <p className="rounded-md bg-destructive/10 p-2.5 text-sm text-destructive">
            {blockedMessage}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogCancel>Keep reservation</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirm} disabled={cancelReservation.isPending}>
            {cancelReservation.isPending ? "Cancelling..." : "Cancel reservation"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
