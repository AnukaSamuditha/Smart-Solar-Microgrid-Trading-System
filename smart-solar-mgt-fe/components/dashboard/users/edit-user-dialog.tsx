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
import { useUpdateUser } from "@/hooks/use-users"
import { toApiError } from "@/lib/api/errors"
import type { UserListItem } from "@/lib/api/users"
import { updateUserSchema, type UpdateUserFormValues } from "@/lib/validations/user"

interface EditUserDialogProps {
  user: UserListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// role and status are immutable here — only email/username can be edited, matching the backend
export function EditUserDialog({ user, open, onOpenChange }: EditUserDialogProps) {
  const updateUser = useUpdateUser()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<UpdateUserFormValues>({
    resolver: zodResolver(updateUserSchema),
    values: { username: user.username ?? "", email: user.email },
  })

  const onSubmit = handleSubmit((values) => {
    updateUser.mutate(
      { id: user.id, ...values },
      {
        onSuccess: () => {
          toast.success(`${values.username} updated.`)
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
            <DialogTitle>Edit user</DialogTitle>
            <DialogDescription>
              Role ({user.role === "Backoffice" ? "Backoffice" : "Grid Operator"}) can&apos;t be
              changed here.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.username}>
              <FieldLabel htmlFor={`edit-username-${user.id}`}>Username</FieldLabel>
              <Controller
                name="username"
                control={control}
                render={({ field }) => (
                  <Input
                    id={`edit-username-${user.id}`}
                    aria-invalid={!!errors.username}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.username]} />
            </Field>
            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor={`edit-email-${user.id}`}>Email</FieldLabel>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <Input
                    id={`edit-email-${user.id}`}
                    type="email"
                    aria-invalid={!!errors.email}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.email]} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={updateUser.isPending}>
              {updateUser.isPending ? "Saving..." : "Save changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
