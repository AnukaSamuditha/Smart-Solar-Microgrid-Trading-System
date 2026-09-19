"use client"

import { BatterySlotsPanel } from "@/components/dashboard/nodes/battery-slots-panel"
import { useNodes } from "@/hooks/use-nodes"

// available to both Backoffice and Grid Operator (see lib/nav-config.ts — no roles restriction
// on this nav entry); Grid Operators use it day-to-day, per project-specification.md section 6
export default function BatterySlotsPage() {
  const { data, isPending } = useNodes({
    status: "Active",
    page: 1,
    pageSize: 100,
    sortBy: "Name",
    sortDir: "asc",
  })

  return (
    <div className="flex flex-1 flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold">Battery Slots</h1>
        <p className="text-sm text-muted-foreground">
          Available battery storage capacity per grid node
        </p>
      </div>

      {isPending ? (
        <p className="py-10 text-center text-sm text-muted-foreground">Loading nodes...</p>
      ) : (
        <BatterySlotsPanel nodes={data?.items ?? []} />
      )}
    </div>
  )
}
