"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
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
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useUpdateReservation } from "@/hooks/use-reservations"
import { toApiError } from "@/lib/api/errors"
import type { Reservation } from "@/lib/api/reservations"
import { toApiDateTime, toInputDateTime } from "@/lib/time"
import { updateReservationSchema, type UpdateReservationFormValues } from "@/lib/validations/reservation"

interface EditReservationDialogProps {
  reservation: Reservation
  open: boolean
  onOpenChange: (open: boolean) => void
}

// reschedule-only: the node and slot stay fixed (matches the backend's UpdateAsync contract —
// changing node/slot requires cancelling and creating a new reservation)
export function EditReservationDialog({ reservation, open, onOpenChange }: EditReservationDialogProps) {
  const updateReservation = useUpdateReservation()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<UpdateReservationFormValues>({
    resolver: zodResolver(updateReservationSchema),
    values: {
      startTime: toInputDateTime(reservation.startTime),
      endTime: toInputDateTime(reservation.endTime),
    },
  })

  const onSubmit = handleSubmit((values) => {
    const startTime = toApiDateTime(values.startTime)
    const endTime = toApiDateTime(values.endTime)
    if (!startTime || !endTime) {
      toast.error("Enter a valid start and end time.")
      return
    }

    updateReservation.mutate(
      { id: reservation.id, startTime, endTime },
      {
        onSuccess: () => {
          toast.success("Reservation rescheduled.")
          onOpenChange(false)
        },
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={onSubmit}>
          <DialogHeader>
            <DialogTitle>Reschedule reservation</DialogTitle>
            <DialogDescription>
              Node and battery slot stay the same. Rescheduling requires at least 12 hours&apos;
              notice from the current start time.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.startTime}>
              <FieldLabel htmlFor="edit-reservation-start">Start time</FieldLabel>
              <Controller
                name="startTime"
                control={control}
                render={({ field }) => (
                  <Input
                    id="edit-reservation-start"
                    type="datetime-local"
                    aria-invalid={!!errors.startTime}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.startTime]} />
            </Field>
            <Field data-invalid={!!errors.endTime}>
              <FieldLabel htmlFor="edit-reservation-end">End time</FieldLabel>
              <Controller
                name="endTime"
                control={control}
                render={({ field }) => (
                  <Input
                    id="edit-reservation-end"
                    type="datetime-local"
                    aria-invalid={!!errors.endTime}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.endTime]} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={updateReservation.isPending}>
              {updateReservation.isPending ? "Saving..." : "Save changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
