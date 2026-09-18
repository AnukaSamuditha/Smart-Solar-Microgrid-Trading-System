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
import { useUpdateProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import type { ProsumerListItem } from "@/lib/api/prosumers"
import { updateProsumerSchema, type UpdateProsumerFormValues } from "@/lib/validations/prosumer"

interface EditProsumerDialogProps {
  prosumer: ProsumerListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// NIC is immutable here — only email/full name can be edited, matching the backend
export function EditProsumerDialog({ prosumer, open, onOpenChange }: EditProsumerDialogProps) {
  const updateProsumer = useUpdateProsumer()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<UpdateProsumerFormValues>({
    resolver: zodResolver(updateProsumerSchema),
    values: { email: prosumer.email, fullName: prosumer.fullName ?? "" },
  })

  const onSubmit = handleSubmit((values) => {
    updateProsumer.mutate(
      { nic: prosumer.nic, ...values },
      {
        onSuccess: () => {
          toast.success(`${prosumer.nic} updated.`)
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
            <DialogTitle>Edit prosumer</DialogTitle>
            <DialogDescription>NIC ({prosumer.nic}) can&apos;t be changed here.</DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.fullName}>
              <FieldLabel htmlFor={`edit-prosumer-full-name-${prosumer.nic}`}>
                Full name (optional)
              </FieldLabel>
              <Controller
                name="fullName"
                control={control}
                render={({ field }) => (
                  <Input
                    id={`edit-prosumer-full-name-${prosumer.nic}`}
                    aria-invalid={!!errors.fullName}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.fullName]} />
            </Field>
            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor={`edit-prosumer-email-${prosumer.nic}`}>Email</FieldLabel>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <Input
                    id={`edit-prosumer-email-${prosumer.nic}`}
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
            <Button type="submit" disabled={updateProsumer.isPending}>
              {updateProsumer.isPending ? "Saving..." : "Save changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
