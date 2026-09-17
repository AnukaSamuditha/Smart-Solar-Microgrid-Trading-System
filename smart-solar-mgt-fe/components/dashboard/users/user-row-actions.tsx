"use client"

import { useState } from "react"
import { MoreHorizontalIcon } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useReactivateUser } from "@/hooks/use-users"
import { toApiError } from "@/lib/api/errors"
import type { UserListItem } from "@/lib/api/users"

import { DeactivateUserDialog } from "./deactivate-user-dialog"
import { DeleteUserDialog } from "./delete-user-dialog"
import { EditUserDialog } from "./edit-user-dialog"

// reactivate is non-destructive (no confirmation); deactivate/delete both require confirmation,
// with delete's copy escalated since it's irreversible
export function UserRowActions({ user }: { user: UserListItem }) {
  const [editOpen, setEditOpen] = useState(false)
  const [deactivateOpen, setDeactivateOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const reactivate = useReactivateUser()

  const handleReactivate = () => {
    reactivate.mutate(user.id, {
      onSuccess: () => toast.success(`${user.username ?? user.email} reactivated.`),
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <MoreHorizontalIcon />
          <span className="sr-only">User actions</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => setEditOpen(true)}>Edit</DropdownMenuItem>
          {user.status === "Deactivated" ? (
            <DropdownMenuItem onClick={handleReactivate} disabled={reactivate.isPending}>
              Reactivate
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => setDeactivateOpen(true)}>
              Deactivate
            </DropdownMenuItem>
          )}
          <DropdownMenuItem variant="destructive" onClick={() => setDeleteOpen(true)}>
            Delete permanently
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <EditUserDialog user={user} open={editOpen} onOpenChange={setEditOpen} />
      <DeactivateUserDialog user={user} open={deactivateOpen} onOpenChange={setDeactivateOpen} />
      <DeleteUserDialog user={user} open={deleteOpen} onOpenChange={setDeleteOpen} />
    </>
  )
}
