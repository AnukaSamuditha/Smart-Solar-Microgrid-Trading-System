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
import { useCreateProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import { createProsumerSchema, type CreateProsumerFormValues } from "@/lib/validations/prosumer"

interface CreateProsumerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// same invitation-based flow as CreateUserDialog — sends a setup invitation, doesn't set a
// password directly (a Backoffice/Grid Operator choosing a prosumer's password isn't safe)
export function CreateProsumerDialog({ open, onOpenChange }: CreateProsumerDialogProps) {
  const createProsumer = useCreateProsumer()

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateProsumerFormValues>({
    resolver: zodResolver(createProsumerSchema),
    defaultValues: { nic: "", email: "", fullName: "" },
  })

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      reset()
    }
    onOpenChange(next)
  }

  const onSubmit = handleSubmit((values) => {
    createProsumer.mutate(values, {
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
            <DialogTitle>Add prosumer</DialogTitle>
            <DialogDescription>
              NIC is the profile&apos;s permanent identifier and can&apos;t be changed later.
              They&apos;ll receive an email invitation to set up their password.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.nic}>
              <FieldLabel htmlFor="create-prosumer-nic">NIC</FieldLabel>
              <Controller
                name="nic"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-prosumer-nic"
                    placeholder="200012345678"
                    aria-invalid={!!errors.nic}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.nic]} />
            </Field>
            <Field data-invalid={!!errors.fullName}>
              <FieldLabel htmlFor="create-prosumer-full-name">Full name (optional)</FieldLabel>
              <Controller
                name="fullName"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-prosumer-full-name"
                    aria-invalid={!!errors.fullName}
                    {...field}
                  />
                )}
              />
              <FieldError errors={[errors.fullName]} />
            </Field>
            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor="create-prosumer-email">Email</FieldLabel>
              <Controller
                name="email"
                control={control}
                render={({ field }) => (
                  <Input
                    id="create-prosumer-email"
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
            <Button type="submit" disabled={createProsumer.isPending}>
              {createProsumer.isPending ? "Adding..." : "Add prosumer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
