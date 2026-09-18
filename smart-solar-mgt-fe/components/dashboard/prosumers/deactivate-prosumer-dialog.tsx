"use client"

import { toast } from "sonner"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { useDeactivateProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import type { ProsumerListItem } from "@/lib/api/prosumers"

interface DeactivateProsumerDialogProps {
  prosumer: ProsumerListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// soft-delete: blocks the profile without removing its data; reversible via Reactivate
// (Backoffice-only — see ProsumerRowActions)
export function DeactivateProsumerDialog({
  prosumer,
  open,
  onOpenChange,
}: DeactivateProsumerDialogProps) {
  const deactivate = useDeactivateProsumer()
  const label = prosumer.fullName ?? prosumer.nic

  const handleConfirm = () => {
    deactivate.mutate(prosumer.nic, {
      onSuccess: () => {
        toast.success(`${label} has been deactivated.`)
        onOpenChange(false)
      },
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Deactivate {label}?</AlertDialogTitle>
          <AlertDialogDescription>
            This profile will be blocked immediately. Only a Backoffice officer can reactivate it
            afterward.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirm} disabled={deactivate.isPending}>
            {deactivate.isPending ? "Deactivating..." : "Deactivate"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
