"use client"

import { useMemo, useState } from "react"
import { SearchIcon } from "lucide-react"

import { NodeStatusBadge } from "@/components/dashboard/nodes/node-status-badge"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useTodaysReservations } from "@/hooks/use-dashboard"
import { useNodes } from "@/hooks/use-nodes"

import { classifyReservation } from "./reservation-activity"

function reservationsCell(counts: { active: number; scheduled: number } | undefined) {
  if (!counts || (counts.active === 0 && counts.scheduled === 0)) {
    return { dotClassName: "bg-muted-foreground/40", text: "None today" }
  }
  if (counts.active > 0) {
    return {
      dotClassName: "bg-primary",
      text: `${counts.active} Active${counts.scheduled > 0 ? ` · ${counts.scheduled} Scheduled` : ""}`,
    }
  }
  return { dotClassName: "bg-amber-500", text: `${counts.scheduled} Scheduled` }
}

function utilizationClassName(pct: number): string {
  if (pct >= 90) return "bg-destructive"
  if (pct >= 60) return "bg-amber-500"
  return "bg-primary"
}

function batterySlotsClassName(availablePct: number): string {
  if (availablePct <= 10) return "text-destructive"
  if (availablePct <= 30) return "text-amber-600 dark:text-amber-400"
  return "text-primary"
}

// dashboard's live, read-only node-status panel: at-a-glance capacity, battery-slot availability,
// and today's reservation activity per node. Purely informational — unlike battery-slots/page.tsx
// or grid-nodes/page.tsx, it has no row actions or drill-down, so it can't be used to manage a
// node from the dashboard (that stays the job of the dedicated Grid Nodes / Battery Slots routes).
export function ActiveHubNodesTable() {
  const [search, setSearch] = useState("")
  const { data, isPending } = useNodes({
    status: "Active",
    page: 1,
    pageSize: 100,
    sortBy: "Name",
    sortDir: "asc",
  })
  const { data: reservationsData } = useTodaysReservations()

  const allNodes = useMemo(() => data?.items ?? [], [data])
  const nodes = useMemo(() => {
    const term = search.trim().toLowerCase()
    return term ? allNodes.filter((node) => node.name.toLowerCase().includes(term)) : allNodes
  }, [allNodes, search])

  const reservationCountsByNode = useMemo(() => {
    const now = new Date()
    const counts = new Map<string, { active: number; scheduled: number }>()
    for (const reservation of reservationsData?.items ?? []) {
      const bucket = classifyReservation(reservation, now)
      if (bucket === "completed") {
        continue
      }
      const entry = counts.get(reservation.nodeId) ?? { active: 0, scheduled: 0 }
      if (bucket === "active") {
        entry.active += 1
      } else {
        entry.scheduled += 1
      }
      counts.set(reservation.nodeId, entry)
    }
    return counts
  }, [reservationsData])

  return (
    <Card className="flex flex-col gap-0 py-0">
      <CardHeader className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 py-4">
        <div className="flex items-center gap-2">
          <CardTitle className="text-base">Active Hub Nodes</CardTitle>
          <Badge variant="secondary" className="gap-1.5">
            <span className="size-1.5 rounded-full bg-primary" />
            {allNodes.length} Online
          </Badge>
        </div>
        <div className="relative w-full max-w-52">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Filter by node name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-8 pl-8 text-xs"
          />
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {isPending ? (
          <div className="flex flex-col gap-2 p-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-full rounded-md" />
            ))}
          </div>
        ) : nodes.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            {allNodes.length === 0 ? "No active grid nodes yet." : "No nodes match that filter."}
          </p>
        ) : (
          <div className="max-h-96 overflow-y-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-[11px] tracking-wide uppercase">Node code &amp; name</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">State</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">Location</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">Capacity</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">Battery slots</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">Reservations</TableHead>
                  <TableHead className="text-[11px] tracking-wide uppercase">Utilization</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {nodes.map((node) => {
                  const total = node.batterySlots.length
                  const availableCount = node.batterySlots.filter(
                    (slot) => slot.status === "Available"
                  ).length
                  const availablePct = total === 0 ? 0 : (availableCount / total) * 100
                  const utilizationPct = total === 0 ? 0 : Math.round(100 - availablePct)
                  const reservations = reservationsCell(reservationCountsByNode.get(node.id))

                  return (
                    <TableRow key={node.id}>
                      <TableCell>
                        <p className="font-medium">{node.name}</p>
                        <p className="font-mono text-xs text-muted-foreground">
                          {node.id.slice(-6).toUpperCase()}
                        </p>
                      </TableCell>
                      <TableCell>
                        <NodeStatusBadge status={node.status} />
                      </TableCell>
                      <TableCell className="font-mono text-xs whitespace-nowrap text-muted-foreground">
                        {node.latitude.toFixed(2)}, {node.longitude.toFixed(2)}
                      </TableCell>
                      <TableCell className="font-mono text-sm tabular-nums text-muted-foreground">
                        {node.capacityKw.toLocaleString()} kW
                      </TableCell>
                      <TableCell className={`font-mono text-sm font-medium tabular-nums ${batterySlotsClassName(availablePct)}`}>
                        {availableCount}/{total} Avail
                      </TableCell>
                      <TableCell className="text-sm whitespace-nowrap">
                        <span className="flex items-center gap-1.5">
                          <span className={`size-1.5 rounded-full ${reservations.dotClassName}`} />
                          <span className="text-muted-foreground">{reservations.text}</span>
                        </span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-20 overflow-hidden rounded-full bg-muted">
                            <div
                              className={`h-full ${utilizationClassName(utilizationPct)}`}
                              style={{ width: `${utilizationPct}%` }}
                            />
                          </div>
                          <span className="font-mono text-xs text-muted-foreground">{utilizationPct}%</span>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
