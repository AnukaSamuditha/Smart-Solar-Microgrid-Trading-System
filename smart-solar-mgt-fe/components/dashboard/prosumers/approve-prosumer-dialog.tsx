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
import { useApproveProsumer } from "@/hooks/use-prosumers"
import { toApiError } from "@/lib/api/errors"
import type { ProsumerListItem } from "@/lib/api/prosumers"

interface ApproveProsumerDialogProps {
  prosumer: ProsumerListItem
  open: boolean
  onOpenChange: (open: boolean) => void
}

// approving a self-registered (PendingApproval) request emails the prosumer a code to set their
// password in the mobile app (ProsumerMobileApprovalEmailTemplate) - not a web link, since this
// prosumer registered from the app and finishes setup there too
export function ApproveProsumerDialog({
  prosumer,
  open,
  onOpenChange,
}: ApproveProsumerDialogProps) {
  const approve = useApproveProsumer()
  const label = prosumer.fullName ?? prosumer.nic

  const handleConfirm = () => {
    approve.mutate(prosumer.nic, {
      onSuccess: () => {
        toast.success(`${label} approved. They've been emailed a code to set their password.`)
        onOpenChange(false)
      },
      onError: (error) => toast.error(toApiError(error).message),
    })
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Approve {label}?</AlertDialogTitle>
          <AlertDialogDescription>
            They&apos;ll be emailed a code to set their password in the mobile app.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted-foreground">NIC</dt>
          <dd>{prosumer.nic}</dd>
          <dt className="text-muted-foreground">Email</dt>
          <dd>{prosumer.email}</dd>
          <dt className="text-muted-foreground">Phone</dt>
          <dd>{prosumer.phone ?? "—"}</dd>
          <dt className="text-muted-foreground">Address</dt>
          <dd>{prosumer.address ?? "—"}</dd>
        </dl>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirm} disabled={approve.isPending}>
            {approve.isPending ? "Approving..." : "Approve"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
