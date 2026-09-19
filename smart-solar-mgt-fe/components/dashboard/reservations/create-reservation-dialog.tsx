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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useNode, useNodes } from "@/hooks/use-nodes"
import { useCreateReservation } from "@/hooks/use-reservations"
import { toApiError } from "@/lib/api/errors"
import { toApiDateTime } from "@/lib/time"
import { createReservationSchema, type CreateReservationFormValues } from "@/lib/validations/reservation"

interface CreateReservationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// staff-assisted booking (e.g. over the phone) — there's no prosumer login on this dashboard, so
// the NIC is typed in directly rather than resolved from a logged-in session
export function CreateReservationDialog({ open, onOpenChange }: CreateReservationDialogProps) {
  const createReservation = useCreateReservation()
  const { data: nodes } = useNodes({ status: "Active", pageSize: 100 })

  const {
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateReservationFormValues>({
    resolver: zodResolver(createReservationSchema),
    defaultValues: { prosumerNic: "", nodeId: "", slotId: "", startTime: "", endTime: "" },
  })

  const selectedNodeId = watch("nodeId")
  const { data: selectedNode } = useNode(selectedNodeId || undefined)
  const availableSlots = selectedNode?.batterySlots.filter((slot) => slot.status === "Available") ?? []

  // base-ui's <Select.Value> only renders a label if the value is registered in this map — it
  // doesn't infer one from mounted <SelectItem> children (see @base-ui/react/select's
  // resolveSelectedLabel), so both dynamic-option selects below need it explicitly
  const nodeSelectItems = Object.fromEntries((nodes?.items ?? []).map((node) => [node.id, node.name]))
  const slotSelectItems = Object.fromEntries(availableSlots.map((slot) => [slot.slotId, `Slot ${slot.slotId}`]))

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      reset()
    }
    onOpenChange(next)
  }

  const onSubmit = handleSubmit((values) => {
    const startTime = toApiDateTime(values.startTime)
    const endTime = toApiDateTime(values.endTime)
    if (!startTime || !endTime) {
      toast.error("Enter a valid start and end time.")
      return
    }

    createReservation.mutate(
      { prosumerNic: values.prosumerNic, nodeId: values.nodeId, slotId: values.slotId, startTime, endTime },
      {
        onSuccess: () => {
          toast.success(`Reservation booked for ${values.prosumerNic}.`)
          handleOpenChange(false)
        },
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  })

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={onSubmit}>
          <DialogHeader>
            <DialogTitle>New reservation</DialogTitle>
            <DialogDescription>
              Book an energy slot on a prosumer&apos;s behalf. The start time must be in the
              future and no more than 7 days out.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.prosumerNic}>
              <FieldLabel htmlFor="create-reservation-nic">Prosumer NIC</FieldLabel>
              <Controller
                name="prosumerNic"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-reservation-nic"
                    placeholder="200012345678"
                    aria-invalid={!!errors.prosumerNic}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.prosumerNic]} />
            </Field>
            <Field data-invalid={!!errors.nodeId}>
              <FieldLabel htmlFor="create-reservation-node">Grid node</FieldLabel>
              <Controller
                name="nodeId"
                control={control}
                render={({ field }) => (
                  <Select
                    items={nodeSelectItems}
                    value={field.value}
                    onValueChange={(value) => {
                      field.onChange(value)
                      setValue("slotId", "")
                    }}
                  >
                    <SelectTrigger id="create-reservation-node" className="w-full" aria-invalid={!!errors.nodeId}>
                      <SelectValue placeholder="Select a node" />
                    </SelectTrigger>
                    <SelectContent>
                      {(nodes?.items ?? []).map((node) => (
                        <SelectItem key={node.id} value={node.id}>
                          {node.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              <FieldError errors={[errors.nodeId]} />
            </Field>
            <Field data-invalid={!!errors.slotId}>
              <FieldLabel htmlFor="create-reservation-slot">Battery slot</FieldLabel>
              <Controller
                name="slotId"
                control={control}
                render={({ field }) => (
                  <Select
                    items={slotSelectItems}
                    value={field.value}
                    onValueChange={field.onChange}
                    disabled={!selectedNodeId}
                  >
                    <SelectTrigger id="create-reservation-slot" className="w-full" aria-invalid={!!errors.slotId}>
                      <SelectValue
                        placeholder={selectedNodeId ? "Select a slot" : "Select a node first"}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {availableSlots.length === 0 ? (
                        <div className="px-2 py-1.5 text-sm text-muted-foreground">
                          No available slots
                        </div>
                      ) : (
                        availableSlots.map((slot) => (
                          <SelectItem key={slot.slotId} value={slot.slotId}>
                            Slot {slot.slotId}
                          </SelectItem>
                        ))
                      )}
                    </SelectContent>
                  </Select>
                )}
              />
              <FieldError errors={[errors.slotId]} />
            </Field>
            <Field data-invalid={!!errors.startTime}>
              <FieldLabel htmlFor="create-reservation-start">Start time</FieldLabel>
              <Controller
                name="startTime"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-reservation-start"
                    type="datetime-local"
                    aria-invalid={!!errors.startTime}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.startTime]} />
            </Field>
            <Field data-invalid={!!errors.endTime}>
              <FieldLabel htmlFor="create-reservation-end">End time</FieldLabel>
              <Controller
                name="endTime"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-reservation-end"
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
            <Button type="submit" disabled={createReservation.isPending}>
              {createReservation.isPending ? "Booking..." : "Book reservation"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
