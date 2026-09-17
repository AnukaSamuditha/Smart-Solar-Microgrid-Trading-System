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
import { useDeactivateUser } from "@/hooks/use-users"
import { toApiError } from "@/lib/api/errors"
import type { UserListItem } from "@/lib/api/users"

interface DeactivateUserDialogProps {
  user: UserListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// soft-delete: blocks sign-in without removing the account or its data; reversible via Reactivate
export function DeactivateUserDialog({ user, open, onOpenChange }: DeactivateUserDialogProps) {
  const deactivate = useDeactivateUser()
  const label = user.username ?? user.email

  const handleConfirm = () => {
    deactivate.mutate(user.id, {
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
            They will immediately lose access to sign in. You can reactivate this account at any
            time.
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
