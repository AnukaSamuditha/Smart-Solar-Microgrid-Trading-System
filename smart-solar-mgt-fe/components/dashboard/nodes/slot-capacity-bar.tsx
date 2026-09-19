import type { BatterySlot } from "@/lib/api/nodes"
import { cn } from "@/lib/utils"

import { BATTERY_SLOT_STATUS_META, BATTERY_SLOT_STATUS_OPTIONS } from "./battery-slot-status"

interface SlotCapacityBarProps {
  slots: BatterySlot[]
  className?: string
}

// a fixed-width, 100%-stacked proportional bar: it stays exactly the same size whether a node
// has 4 slots or 4,000, unlike rendering one visual element per slot (which stops being
// scannable well before three-digit counts — see BatterySlotsSheet for the per-slot detail view)
export function SlotCapacityBar({ slots, className }: SlotCapacityBarProps) {
  const total = slots.length

  if (total === 0) {
    return <div className={cn("h-2 w-full rounded-full bg-muted", className)} />
  }

  return (
    <div className={cn("flex h-2 w-full overflow-hidden rounded-full bg-muted", className)}>
      {BATTERY_SLOT_STATUS_OPTIONS.map((status) => {
        const count = slots.filter((slot) => slot.status === status).length
        if (count === 0) {
          return null
        }

        return (
          <div
            key={status}
            className={BATTERY_SLOT_STATUS_META[status].barClassName}
            style={{ width: `${(count / total) * 100}%` }}
            title={`${status}: ${count}`}
          />
        )
      })}
    </div>
  )
}
