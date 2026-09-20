"use client"

import { BATTERY_SLOT_STATUS_META, BATTERY_SLOT_STATUS_OPTIONS } from "@/components/dashboard/nodes/battery-slot-status"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useDashboardSummary } from "@/hooks/use-dashboard"
import { useNodes } from "@/hooks/use-nodes"
import type { BatterySlot } from "@/lib/api/nodes"

// same active-nodes fetch as active-hub-nodes-table.tsx — shares its TanStack Query cache entry
function usePerNodeSlots() {
  const { data } = useNodes({
    status: "Active",
    page: 1,
    pageSize: 100,
    sortBy: "Name",
    sortDir: "asc",
  })
  return data?.items ?? []
}

// one small square per battery slot, colored by status — a discrete block view rather than
// SlotCapacityBar's proportional bar, so an operator can see exactly which slots (not just what
// share) are tied up
function SlotBlocks({ slots }: { slots: BatterySlot[] }) {
  if (slots.length === 0) {
    return (
      <p className="rounded-md bg-black/10 p-2 text-xs text-muted-foreground dark:bg-black/40">
        No battery slots configured.
      </p>
    )
  }

  return (
    <div className="flex flex-wrap gap-1 rounded-md bg-black/10 p-2 dark:bg-black/40">
      {slots.map((slot) => (
        <span
          key={slot.slotId}
          title={`Slot ${slot.slotId}: ${slot.status}`}
          className={`size-3.5 rounded-sm ${BATTERY_SLOT_STATUS_META[slot.status].barClassName}`}
        />
      ))}
    </div>
  )
}

export function BatterySlotDistribution() {
  const { data: summary, isPending } = useDashboardSummary()
  const nodes = usePerNodeSlots()

  return (
    <Card className="flex flex-col gap-0 py-0">
      <CardHeader className="flex items-center justify-between border-b border-border/60 py-4">
        <CardTitle className="text-base">Battery Slot Distribution</CardTitle>
        {summary ? (
          <Badge variant="secondary" className="shrink-0">
            {summary.batterySlots.available}/{summary.batterySlots.total} Ready
          </Badge>
        ) : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-5 p-4">
        {isPending || !summary ? (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-2.5 w-full rounded-full" />
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-full rounded-md" />
            ))}
          </div>
        ) : summary.batterySlots.total === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            No battery slots configured yet.
          </p>
        ) : (
          <>
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium">Aggregated Pool</span>
                <span className="font-mono text-muted-foreground">
                  {Math.round((summary.batterySlots.available / summary.batterySlots.total) * 100)}% Free
                </span>
              </div>
              <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted">
                {BATTERY_SLOT_STATUS_OPTIONS.map((status) => {
                  const count = summary.batterySlots[
                    status === "Available" ? "available" : status === "Reserved" ? "reserved" : "occupied"
                  ]
                  if (count === 0) return null
                  return (
                    <div
                      key={status}
                      className={BATTERY_SLOT_STATUS_META[status].barClassName}
                      style={{ width: `${(count / summary.batterySlots.total) * 100}%` }}
                      title={`${status}: ${count}`}
                    />
                  )
                })}
              </div>
            </div>

            <div className="flex flex-col gap-3">
              {nodes.map((node) => {
                const availableCount = node.batterySlots.filter((s) => s.status === "Available").length
                const reservedCount = node.batterySlots.filter((s) => s.status === "Reserved").length
                const occupiedCount = node.batterySlots.filter((s) => s.status === "Occupied").length

                return (
                  <div key={node.id} className="flex flex-col gap-1.5">
                    <div className="flex items-baseline justify-between gap-2 text-xs">
                      <span className="truncate font-medium">
                        {node.name}{" "}
                        <span className="font-mono font-normal text-muted-foreground">
                          ({node.id.slice(-6).toUpperCase()})
                        </span>
                      </span>
                      <span className="shrink-0 font-mono text-muted-foreground">
                        {availableCount} Avail · {reservedCount} Rsrv · {occupiedCount} In-Use
                      </span>
                    </div>
                    <SlotBlocks slots={node.batterySlots} />
                  </div>
                )
              })}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
