import { z } from "zod"

// old Sri Lankan NIC: 9 digits + V or X; new Sri Lankan NIC: 12 digits (matches the backend's
// NicPattern in Endpoints/ReservationEndpoints.cs / ProsumerEndpoints.cs)
const nicSchema = z
  .string()
  .min(1, "NIC is required.")
  .regex(/^([0-9]{9}[vVxX]|[0-9]{12})$/, "Enter a valid NIC (e.g. 123456789V or 200012345678).")

// startTime/endTime are "yyyy-MM-ddTHH:mm" strings straight from <input type="datetime-local">;
// the future/7-day checks parse through Date since they compare against the current instant, not
// each other. Mirrors the backend's ValidateReservationWindow in Endpoints/ReservationEndpoints.cs.
export const createReservationSchema = z
  .object({
    prosumerNic: nicSchema,
    nodeId: z.string().min(1, "Select a grid node."),
    slotId: z.string().min(1, "Select a battery slot."),
    startTime: z.string().min(1, "Enter a start time."),
    endTime: z.string().min(1, "Enter an end time."),
  })
  .refine((values) => values.startTime < values.endTime, {
    message: "Start time must be before the end time.",
    path: ["endTime"],
  })
  .refine((values) => new Date(values.startTime).getTime() > Date.now(), {
    message: "Start time must be in the future.",
    path: ["startTime"],
  })
  .refine(
    (values) => new Date(values.startTime).getTime() <= Date.now() + 7 * 24 * 60 * 60 * 1000,
    { message: "Reservations must be scheduled within 7 days.", path: ["startTime"] }
  )

export type CreateReservationFormValues = z.infer<typeof createReservationSchema>

export const updateReservationSchema = z
  .object({
    startTime: z.string().min(1, "Enter a start time."),
    endTime: z.string().min(1, "Enter an end time."),
  })
  .refine((values) => values.startTime < values.endTime, {
    message: "Start time must be before the end time.",
    path: ["endTime"],
  })
  .refine((values) => new Date(values.startTime).getTime() > Date.now(), {
    message: "Start time must be in the future.",
    path: ["startTime"],
  })
  .refine(
    (values) => new Date(values.startTime).getTime() <= Date.now() + 7 * 24 * 60 * 60 * 1000,
    { message: "Reservations must be scheduled within 7 days.", path: ["startTime"] }
  )

export type UpdateReservationFormValues = z.infer<typeof updateReservationSchema>
