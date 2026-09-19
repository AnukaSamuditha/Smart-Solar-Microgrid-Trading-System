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
import { useReactivateNode } from "@/hooks/use-nodes"
import { toApiError } from "@/lib/api/errors"
import type { MicrogridNode } from "@/lib/api/nodes"

import { DeactivateNodeDialog } from "./deactivate-node-dialog"

// this row only renders on the Backoffice-only "All Nodes" page (see RequireRole in
// app/(dashboard)/grid-nodes/page.tsx), so no per-role gating is needed here, unlike
// ProsumerRowActions which is shared by both roles
export function NodeRowActions({ node }: { node: MicrogridNode }) {
  const [deactivateOpen, setDeactivateOpen] = useState(false)
  const reactivate = useReactivateNode()

  const handleReactivate = () => {
    reactivate.mutate(node.id, {
      onSuccess: () => toast.success(`${node.name} reactivated.`),
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
          <MoreHorizontalIcon />
          <span className="sr-only">Node actions</span>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {node.status === "Deactivated" ? (
            <DropdownMenuItem onClick={handleReactivate} disabled={reactivate.isPending}>
              Reactivate
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem onClick={() => setDeactivateOpen(true)}>Deactivate</DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <DeactivateNodeDialog node={node} open={deactivateOpen} onOpenChange={setDeactivateOpen} />
    </>
  )
}
