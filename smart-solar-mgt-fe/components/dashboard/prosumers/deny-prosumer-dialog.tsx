"use client"

import { useState } from "react"
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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useDenyProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import type { ProsumerListItem } from "@/lib/api/prosumers"

interface DenyProsumerDialogProps {
  prosumer: ProsumerListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// terminal - a denied request has no path back to PendingApproval; the prosumer would need to
// submit a new self-registration
export function DenyProsumerDialog({ prosumer, open, onOpenChange }: DenyProsumerDialogProps) {
  const deny = useDenyProsumer()
  const [reason, setReason] = useState("")
  const label = prosumer.fullName ?? prosumer.nic

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setReason("")
    }
    onOpenChange(next)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    deny.mutate(
      { nic: prosumer.nic, reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          toast.success(`${label}'s request has been denied.`)
          handleOpenChange(false)
        },
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Deny {label}&apos;s request?</DialogTitle>
            <DialogDescription>
              This is terminal - {label} would need to submit a new registration to try again.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field>
              <FieldLabel htmlFor={`deny-prosumer-reason-${prosumer.nic}`}>
                Reason (optional)
              </FieldLabel>
              <Input
                id={`deny-prosumer-reason-${prosumer.nic}`}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Shown to the prosumer"
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" variant="destructive" disabled={deny.isPending}>
              {deny.isPending ? "Denying..." : "Deny request"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
