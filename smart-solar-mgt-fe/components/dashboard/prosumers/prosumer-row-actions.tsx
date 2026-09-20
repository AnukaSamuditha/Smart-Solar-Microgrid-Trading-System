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
import { useMe } from "@/hooks/use-me"
import { useReactivateProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import type { ProsumerListItem } from "@/lib/api/prosumers"

import { ApproveProsumerDialog } from "./approve-prosumer-dialog"
import { DeactivateProsumerDialog } from "./deactivate-prosumer-dialog"
import { DenyProsumerDialog } from "./deny-prosumer-dialog"
import { EditProsumerDialog } from "./edit-prosumer-dialog"

// reactivate is Backoffice-only (project-specification.md section 3.2): Grid Operators never
// see the option, rather than seeing it and being rejected by the API. Approve/deny (a
// PendingApproval self-registration request) are available to both roles, same as create/edit/
// deactivate - see ProsumerManagementPolicy.
export function ProsumerRowActions({ prosumer }: { prosumer: ProsumerListItem }) {
  const [editOpen, setEditOpen] = useState(false)
  const [deactivateOpen, setDeactivateOpen] = useState(false)
  const [approveOpen, setApproveOpen] = useState(false)
  const [denyOpen, setDenyOpen] = useState(false)
  const { data: me } = useMe()
  const canReactivate = me?.role === "Backoffice"
  const reactivate = useReactivateProsumer()

  const handleReactivate = () => {
    reactivate.mutate(prosumer.nic, {
      onSuccess: () => toast.success(`${prosumer.fullName ?? prosumer.nic} reactivated.`),
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <MoreHorizontalIcon />
          <span className="sr-only">Prosumer actions</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {prosumer.status === "PendingApproval" ? (
            <>
              <DropdownMenuItem onClick={() => setApproveOpen(true)}>Approve</DropdownMenuItem>
              <DropdownMenuItem onClick={() => setDenyOpen(true)}>Deny</DropdownMenuItem>
            </>
          ) : (
            <>
              <DropdownMenuItem onClick={() => setEditOpen(true)}>Edit</DropdownMenuItem>
              {prosumer.status === "Deactivated" ? (
                canReactivate && (
                  <DropdownMenuItem onClick={handleReactivate} disabled={reactivate.isPending}>
                    Reactivate
                  </DropdownMenuItem>
                )
              ) : (
                <DropdownMenuItem onClick={() => setDeactivateOpen(true)}>
                  Deactivate
                </DropdownMenuItem>
              )}
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <EditProsumerDialog prosumer={prosumer} open={editOpen} onOpenChange={setEditOpen} />
      <DeactivateProsumerDialog
        prosumer={prosumer}
        open={deactivateOpen}
        onOpenChange={setDeactivateOpen}
      />
      <ApproveProsumerDialog prosumer={prosumer} open={approveOpen} onOpenChange={setApproveOpen} />
      <DenyProsumerDialog prosumer={prosumer} open={denyOpen} onOpenChange={setDenyOpen} />
    </>
  )
}
