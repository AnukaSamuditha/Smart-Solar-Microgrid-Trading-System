import { z } from "zod"

export const createUserSchema = z.object({
  username: z.string().min(1, "Username is required."),
  email: z
    .string()
    .min(1, "Email is required.")
    .email("Enter a valid email address."),
  role: z.enum(["Backoffice", "GridOperator"], {
    error: "Select a role.",
  }),
})

export type CreateUserFormValues = z.infer<typeof createUserSchema>

export const updateUserSchema = z.object({
  username: z.string().min(1, "Username is required."),
  email: z
    .string()
    .min(1, "Email is required.")
    .email("Enter a valid email address."),
})

export type UpdateUserFormValues = z.infer<typeof updateUserSchema>
