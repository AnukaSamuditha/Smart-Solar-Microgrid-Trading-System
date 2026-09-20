"use client"

import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useRecentActivity } from "@/hooks/use-dashboard"
import type { DashboardActivityType } from "@/lib/api/dashboard"

const ACTIVITY_TAGS: Record<DashboardActivityType, { label: string; className: string }> = {
  NodeCreated: { label: "NODE", className: "bg-primary/10 text-primary" },
  ProsumerCreated: { label: "PROSUMER", className: "bg-amber-500/10 text-amber-600 dark:text-amber-400" },
  ReservationCreated: { label: "RESERVATION", className: "bg-muted text-muted-foreground" },
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })
}

// modeled as a chronological audit log: a fixed-width monospace timestamp, a colored type tag,
// then the pre-formatted label the backend already assembled (see ActivityItem.Label)
export function RecentActivityFeed() {
  const { data, isPending } = useRecentActivity(10)
  const items = data?.items ?? []

  return (
    <Card className="flex flex-col gap-0 py-0">
      <CardHeader className="border-b border-border/60 py-4">
        <CardTitle className="text-base">Chronological Activity Log</CardTitle>
        <CardDescription>Latest node, prosumer, and reservation activity</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-1 p-4">
        {isPending ? (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-9 w-full rounded-md" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">No recent activity yet.</p>
        ) : (
          <ul className="flex flex-col divide-y divide-border/60">
            {items.map((item, index) => {
              const tag = ACTIVITY_TAGS[item.type]
              return (
                <li
                  key={`${item.type}-${item.createdAt}-${index}`}
                  className="flex items-start gap-3 py-2.5"
                >
                  <span className="mt-0.5 shrink-0 font-mono text-xs tabular-nums text-muted-foreground">
                    {formatTimestamp(item.createdAt)}
                  </span>
                  <Badge variant="secondary" className={`shrink-0 ${tag.className}`}>
                    {tag.label}
                  </Badge>
                  <span className="min-w-0 flex-1 truncate text-sm">{item.label}</span>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
