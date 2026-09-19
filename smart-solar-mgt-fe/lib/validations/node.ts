import { z } from "zod"

// all fields are kept as plain `number`s in form state (capacityKw/batterySlotCount read via
// e.target.valueAsNumber in create-node-dialog.tsx) rather than using z.coerce.number(), which
// would otherwise split the schema's input/output types and break the zodResolver's typing.
// operatingStartTime/operatingEndTime are "HH:mm" strings straight from <input type="time">,
// which sort lexicographically, so a plain string comparison is enough to order them.
export const createNodeSchema = z
  .object({
    name: z.string().min(1, "Name is required."),
    latitude: z
      .number()
      .min(-90, "Latitude must be between -90 and 90.")
      .max(90, "Latitude must be between -90 and 90."),
    longitude: z
      .number()
      .min(-180, "Longitude must be between -180 and 180.")
      .max(180, "Longitude must be between -180 and 180."),
    capacityKw: z.number().positive("Capacity must be greater than zero."),
    batterySlotCount: z
      .number()
      .int("Enter a whole number.")
      .min(0, "Battery slot count can't be negative."),
    operatingStartTime: z.string().min(1, "Enter an operating start time."),
    operatingEndTime: z.string().min(1, "Enter an operating end time."),
  })
  .refine((values) => values.operatingStartTime < values.operatingEndTime, {
    message: "Operating start time must be before the end time.",
    path: ["operatingEndTime"],
  })

export type CreateNodeFormValues = z.infer<typeof createNodeSchema>
