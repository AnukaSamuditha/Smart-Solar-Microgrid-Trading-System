import { z } from "zod"

// old Sri Lankan NIC: 9 digits + V or X; new Sri Lankan NIC: 12 digits (matches the backend's
// NicPattern in Endpoints/ProsumerEndpoints.cs)
const nicSchema = z
  .string()
  .min(1, "NIC is required.")
  .regex(/^([0-9]{9}[vVxX]|[0-9]{12})$/, "Enter a valid NIC (e.g. 123456789V or 200012345678).")

export const createProsumerSchema = z.object({
  nic: nicSchema,
  email: z
    .string()
    .min(1, "Email is required.")
    .email("Enter a valid email address."),
  fullName: z.string().optional(),
})

export type CreateProsumerFormValues = z.infer<typeof createProsumerSchema>

export const updateProsumerSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required.")
    .email("Enter a valid email address."),
  fullName: z.string().optional(),
})

export type UpdateProsumerFormValues = z.infer<typeof updateProsumerSchema>
