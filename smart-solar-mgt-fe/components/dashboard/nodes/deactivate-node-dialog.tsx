"use client"

import { useState } from "react"
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
import { useDeactivateNode } from "@/hooks/use-nodes"
import { toApiError } from "@/lib/api/errors"
import type { MicrogridNode } from "@/lib/api/nodes"

interface DeactivateNodeDialogProps {
  node: MicrogridNode
  open: boolean
  onOpenChange: (open: boolean) => void
}

// blocked (409 ActiveReservationsExist) while active energy reservations exist against the
// node — shown inline here rather than only as a toast, since it's an expected, actionable
// outcome once Reservation Management is built (see
// docs/microgrid-node-management-implementation-plan.md). Today it always succeeds, since
// StubReservationLookupService always reports no active reservations.
export function DeactivateNodeDialog({ node, open, onOpenChange }: DeactivateNodeDialogProps) {
  const [blockedMessage, setBlockedMessage] = useState<string | null>(null)
  const deactivate = useDeactivateNode()

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setBlockedMessage(null)
    }
    onOpenChange(next)
  }

  const handleConfirm = () => {
    setBlockedMessage(null)
    deactivate.mutate(node.id, {
      onSuccess: () => {
        toast.success(`${node.name} has been deactivated.`)
        handleOpenChange(false)
      },
      onError: (error) => {
        const apiError = toApiError(error)
        if (apiError.code === "ActiveReservationsExist") {
          setBlockedMessage(apiError.message)
          return
        }
        toast.error(apiError.message)
      },
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Deactivate {node.name}?</AlertDialogTitle>
          <AlertDialogDescription>
            This node will stop accepting new activity immediately. It can be reactivated
            afterward.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {blockedMessage ? (
          <p className="rounded-md bg-destructive/10 p-2.5 text-sm text-destructive">
            {blockedMessage}
          </p>
        ) : null}
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
