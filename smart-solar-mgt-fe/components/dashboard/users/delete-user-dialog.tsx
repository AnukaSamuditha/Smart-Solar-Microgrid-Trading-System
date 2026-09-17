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
import { useDeleteUser } from "@/hooks/use-users"
import { toApiError } from "@/lib/api/errors"
import type { UserListItem } from "@/lib/api/users"

interface DeleteUserDialogProps {
  user: UserListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// hard delete: permanently removes the account and cascades its refresh tokens/invitations
export function DeleteUserDialog({ user, open, onOpenChange }: DeleteUserDialogProps) {
  const deleteUser = useDeleteUser()
  const label = user.username ?? user.email

  const handleConfirm = () => {
    deleteUser.mutate(user.id, {
      onSuccess: () => {
        toast.success(`${label} has been permanently deleted.`)
        onOpenChange(false)
      },
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Permanently delete {label}?</AlertDialogTitle>
          <AlertDialogDescription>
            This cannot be undone. Their account, refresh sessions, and any pending invitation
            will be permanently removed.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={handleConfirm}
            disabled={deleteUser.isPending}
          >
            {deleteUser.isPending ? "Deleting..." : "Delete permanently"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
