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
import { useCreateUser } from "@/hooks/use-users"
import { toApiError } from "@/lib/api/errors"
import { createUserSchema, type CreateUserFormValues } from "@/lib/validations/user"

interface CreateUserDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// admin-facing "invite a new account" form — sends the invitation email, doesn't set a password
export function CreateUserDialog({ open, onOpenChange }: CreateUserDialogProps) {
  const createUser = useCreateUser()

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { username: "", email: "", role: "GridOperator" },
  })

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      reset()
    }
    onOpenChange(next)
  }

  const onSubmit = handleSubmit((values) => {
    createUser.mutate(values, {
      onSuccess: () => {
        toast.success(`Invitation sent to ${values.email}.`)
        handleOpenChange(false)
      },
      onError: (error) => toast.error(toApiError(error).message),
    })
  })

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={onSubmit}>
          <DialogHeader>
            <DialogTitle>Add user</DialogTitle>
            <DialogDescription>
              They&apos;ll receive an email invitation to set up their password.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.username}>
              <FieldLabel htmlFor="create-username">Username</FieldLabel>
              <Controller
                name="username"
                control={control}
                render={({ field }) => (
                  <Input id="create-username" aria-invalid={!!errors.username} {...field} />
                )}
              />
              <FieldError errors={[errors.username]} />
            </Field>
            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor="create-email">Email</FieldLabel>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-email"
                    type="email"
                    aria-invalid={!!errors.email}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.email]} />
            </Field>
            <Field data-invalid={!!errors.role}>
              <FieldLabel htmlFor="create-role">Role</FieldLabel>
              <Controller
                name="role"
                control={control}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="create-role" className="w-full">
                      <SelectValue placeholder="Select a role" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Backoffice">Backoffice</SelectItem>
                      <SelectItem value="GridOperator">Grid Operator</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
              <FieldError errors={[errors.role]} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={createUser.isPending}>
              {createUser.isPending ? "Adding..." : "Add user"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
