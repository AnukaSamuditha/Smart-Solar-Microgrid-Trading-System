"use client"

import { CheckIcon } from "lucide-react"
import { toast } from "sonner"

import { Badge, badgeVariants } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useUpdateBatterySlotStatus } from "@/hooks/use-nodes"
import { toApiError } from "@/lib/api/errors"
import type { BatterySlot, BatterySlotStatus, MicrogridNode } from "@/lib/api/nodes"
import { cn } from "@/lib/utils"

import { BATTERY_SLOT_STATUS_META, BATTERY_SLOT_STATUS_OPTIONS } from "./battery-slot-status"

// per-slot status control used inside a BatterySlotsSheet table row. Only ever renders one
// page's worth of rows at a time (see battery-slots-sheet.tsx), so a control per row stays fine
// no matter how many slots a node has in total.
export function BatterySlotStatusControl({ node, slot }: { node: MicrogridNode; slot: BatterySlot }) {
  const updateStatus = useUpdateBatterySlotStatus()

  const handleChange = (status: BatterySlotStatus) => {
    if (status === slot.status) {
      return
    }

    updateStatus.mutate(
      { id: node.id, slotId: slot.slotId, status },
      {
        onSuccess: () => toast.success(`Slot ${slot.slotId} on ${node.name} set to ${status}.`),
        onError: (error) => toast.error(toApiError(error).message),
      }
    )
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          // Base UI's Menu.Trigger requires a real <button> render target — Badge defaults to a
          // <span>, so the button is styled directly with badgeVariants() instead of nesting one
          <button
            type="button"
            className={cn(
              badgeVariants({ variant: "outline" }),
              BATTERY_SLOT_STATUS_META[slot.status].badgeClassName,
              "h-6 cursor-pointer gap-1.5 px-2.5 text-xs data-popup-open:ring-2 data-popup-open:ring-ring/50"
            )}
          />
        }
      >
        {slot.status}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {BATTERY_SLOT_STATUS_OPTIONS.map((status) => (
          <DropdownMenuItem key={status} onClick={() => handleChange(status)}>
            <Badge
              variant="outline"
              className={cn("size-2 rounded-full p-0", BATTERY_SLOT_STATUS_META[status].badgeClassName)}
            />
            {status}
            {status === slot.status ? <CheckIcon className="ml-auto size-3.5" /> : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
