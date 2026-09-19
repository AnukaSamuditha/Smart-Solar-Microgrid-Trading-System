"use client"

import { useCallback } from "react"
import dynamic from "next/dynamic"
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
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useCreateNode } from "@/hooks/use-nodes"
import { toApiError } from "@/lib/api/errors"
import { toApiTime } from "@/lib/time"
import { createNodeSchema, type CreateNodeFormValues } from "@/lib/validations/node"

// Leaflet touches `window` at import time, so it can't render during Next.js's server pass —
// load it client-only, after the dialog has actually opened
const NodeMapPicker = dynamic(
  () => import("./node-map-picker").then((mod) => mod.NodeMapPicker),
  { ssr: false, loading: () => <div className="min-h-72 w-full flex-1 animate-pulse rounded-lg bg-muted" /> }
)

interface CreateNodeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// defaults the map to Colombo, Sri Lanka until the operator drops a pin elsewhere
const DEFAULT_LATITUDE = 6.9271
const DEFAULT_LONGITUDE = 79.8612
const DEFAULT_OPERATING_START_TIME = "06:00"
const DEFAULT_OPERATING_END_TIME = "18:00"

// registers a new solar grid hub: name, GPS location (via map pin), capacity, a battery slot
// count that seeds that many Available slots, and a daily operating window that seeds the same
// hours on every day of the initial weekly schedule (see MicrogridNodeService) — refinable
// per-day afterward on the Node Schedules page
export function CreateNodeDialog({ open, onOpenChange }: CreateNodeDialogProps) {
  const createNode = useCreateNode()

  const {
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateNodeFormValues>({
    resolver: zodResolver(createNodeSchema),
    defaultValues: {
      name: "",
      latitude: DEFAULT_LATITUDE,
      longitude: DEFAULT_LONGITUDE,
      capacityKw: 0,
      batterySlotCount: 0,
      operatingStartTime: DEFAULT_OPERATING_START_TIME,
      operatingEndTime: DEFAULT_OPERATING_END_TIME,
    },
  })

  const latitude = watch("latitude")
  const longitude = watch("longitude")

  const handleLocationChange = useCallback(
    ({ latitude: lat, longitude: lng }: { latitude: number; longitude: number }) => {
      setValue("latitude", lat, { shouldValidate: true })
      setValue("longitude", lng, { shouldValidate: true })
    },
    [setValue]
  )

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      reset()
    }
    onOpenChange(next)
  }

  const onSubmit = handleSubmit((values) => {
    createNode.mutate(
      {
        ...values,
        operatingStartTime: toApiTime(values.operatingStartTime) as string,
        operatingEndTime: toApiTime(values.operatingEndTime) as string,
      },
      {
        onSuccess: (node) => {
          toast.success(`${node.name} registered.`)
          handleOpenChange(false)
        },
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  })

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex h-[80vh] w-[70vw] max-w-none flex-col overflow-hidden p-6 sm:max-w-none">
        <form onSubmit={onSubmit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="shrink-0">
            <DialogTitle className="text-lg">Register a grid node</DialogTitle>
            <DialogDescription>Click the map to set the hub&apos;s GPS location.</DialogDescription>
          </DialogHeader>
          <div className="grid min-h-0 flex-1 gap-6 py-6 md:grid-cols-2">
            <FieldGroup className="min-h-0 overflow-y-auto pr-1">
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="create-node-name">Name</FieldLabel>
                <Controller
                  name="name"
                  control={control}
                  render={({ field }) => (
                    <Input
                      id="create-node-name"
                      placeholder="Colombo Hub 1"
                      aria-invalid={!!errors.name}
                      {...field}
                    />
                  )}
                />
                <FieldError errors={[errors.name]} />
              </Field>

              <Field data-invalid={!!errors.capacityKw}>
                <FieldLabel htmlFor="create-node-capacity">Capacity (kW/h)</FieldLabel>
                <Controller
                  name="capacityKw"
                  control={control}
                  render={({ field: { onChange, value, ...field } }) => (
                    <Input
                      id="create-node-capacity"
                      type="number"
                      step="0.1"
                      min="0"
                      aria-invalid={!!errors.capacityKw}
                      // a cleared input's valueAsNumber is NaN, which React refuses to render as
                      // a controlled `value` — fall back to "" so the field can be emptied while
                      // typing; zod's z.number() still rejects NaN on submit
                      value={Number.isNaN(value) ? "" : value}
                      onChange={(e) => onChange(e.target.valueAsNumber)}
                      {...field}
                    />
                  )}
                />
                <FieldError errors={[errors.capacityKw]} />
              </Field>

              <Field data-invalid={!!errors.batterySlotCount}>
                <FieldLabel htmlFor="create-node-slots">Battery storage slots</FieldLabel>
                <Controller
                  name="batterySlotCount"
                  control={control}
                  render={({ field: { onChange, value, ...field } }) => (
                    <Input
                      id="create-node-slots"
                      type="number"
                      step="1"
                      min="0"
                      aria-invalid={!!errors.batterySlotCount}
                      // see the capacityKw field above: NaN from a cleared input can't be a
                      // controlled `value`, so fall back to ""
                      value={Number.isNaN(value) ? "" : value}
                      onChange={(e) => onChange(e.target.valueAsNumber)}
                      {...field}
                    />
                  )}
                />
                <FieldError errors={[errors.batterySlotCount]} />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field data-invalid={!!errors.operatingStartTime}>
                  <FieldLabel htmlFor="create-node-start-time">Operating start time</FieldLabel>
                  <Controller
                    name="operatingStartTime"
                    control={control}
                    render={({ field }) => (
                      <Input
                        id="create-node-start-time"
                        type="time"
                        aria-invalid={!!errors.operatingStartTime}
                        {...field}
                      />
                    )}
                  />
                  <FieldError errors={[errors.operatingStartTime]} />
                </Field>

                <Field data-invalid={!!errors.operatingEndTime}>
                  <FieldLabel htmlFor="create-node-end-time">Operating end time</FieldLabel>
                  <Controller
                    name="operatingEndTime"
                    control={control}
                    render={({ field }) => (
                      <Input
                        id="create-node-end-time"
                        type="time"
                        aria-invalid={!!errors.operatingEndTime}
                        {...field}
                      />
                    )}
                  />
                  <FieldError errors={[errors.operatingEndTime]} />
                </Field>
              </div>
            </FieldGroup>

            <div className="flex min-h-0 flex-col gap-2">
              <FieldLabel>Location</FieldLabel>
              {open ? (
                <NodeMapPicker latitude={latitude} longitude={longitude} onChange={handleLocationChange} />
              ) : (
                <div className="min-h-72 w-full flex-1 rounded-lg bg-muted" />
              )}
              <FieldDescription>
                {latitude.toFixed(5)}, {longitude.toFixed(5)}
              </FieldDescription>
              <FieldError errors={[errors.latitude, errors.longitude]} />
            </div>
          </div>
          <DialogFooter className="-mx-6 -mb-6 shrink-0 p-6">
            <Button type="submit" disabled={createNode.isPending}>
              {createNode.isPending ? "Registering..." : "Register node"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
