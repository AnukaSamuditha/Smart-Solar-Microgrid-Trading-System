"use client"

import {
  BatteryChargingIcon,
  CalendarClockIcon,
  RadioIcon,
  ShieldUserIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react"

import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useDashboardSummary } from "@/hooks/use-dashboard"
import { useMe } from "@/hooks/use-me"
import { useUsers } from "@/hooks/use-users"

interface KpiChip {
  label: string
  tone?: "default" | "amber" | "muted"
}

interface KpiTileProps {
  icon: LucideIcon
  label: string
  value: string
  unit?: string
  badge?: string
  chips?: KpiChip[]
}

const CHIP_TONE_CLASSES: Record<NonNullable<KpiChip["tone"]>, string> = {
  default: "border-border/60 text-foreground",
  amber: "border-amber-500/40 text-amber-600 dark:text-amber-400",
  muted: "border-border/60 text-muted-foreground",
}

function KpiTile({ icon: Icon, label, value, unit, badge, chips }: KpiTileProps) {
  return (
    <Card className="py-0">
      <CardContent className="flex flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Icon className="size-4" />
            </span>
            <p className="text-xs tracking-wide text-muted-foreground uppercase">{label}</p>
          </div>
          {badge ? (
            <span className="rounded-md border border-border/60 px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap text-muted-foreground">
              {badge}
            </span>
          ) : null}
        </div>
        <p className="font-mono text-2xl font-semibold tabular-nums">
          {value}
          {unit ? <span className="ml-1 text-sm font-normal text-muted-foreground">{unit}</span> : null}
        </p>
        {chips && chips.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {chips.map((chip) => (
              <span
                key={chip.label}
                className={`rounded-md border px-2 py-0.5 text-xs font-medium ${CHIP_TONE_CLASSES[chip.tone ?? "default"]}`}
              >
                {chip.label}
              </span>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function KpiTileSkeleton() {
  return (
    <Card className="py-0">
      <CardContent className="flex flex-col gap-3 p-4">
        <div className="flex items-center gap-2">
          <Skeleton className="size-8 shrink-0 rounded-lg" />
          <Skeleton className="h-3 w-20" />
        </div>
        <Skeleton className="h-7 w-14" />
      </CardContent>
    </Card>
  )
}

// only mounts (and only calls useUsers()) for a Backoffice viewer, so a Grid Operator session
// makes zero /users network calls for this tile
function StaffCountTile() {
  const { data: users, isPending } = useUsers()

  if (isPending || !users) {
    return <KpiTileSkeleton />
  }

  const activeCount = users.filter((user) => user.status === "Active").length
  const invitedCount = users.filter((user) => user.status === "Invited").length

  return (
    <KpiTile
      icon={ShieldUserIcon}
      label="Staff accounts"
      value={String(users.length)}
      chips={[
        { label: `${activeCount} active` },
        ...(invitedCount > 0 ? [{ label: `${invitedCount} invited`, tone: "amber" as const }] : []),
      ]}
    />
  )
}

function formatHour(hour: number): string {
  return `${hour.toString().padStart(2, "0")}:00`
}

export function KpiStrip() {
  const { data: summary, isPending } = useDashboardSummary()
  const { data: me } = useMe()
  const isBackoffice = me?.role === "Backoffice"

  if (isPending || !summary) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {Array.from({ length: isBackoffice ? 5 : 4 }).map((_, i) => (
          <KpiTileSkeleton key={i} />
        ))}
      </div>
    )
  }

  const allNodesOnline = summary.nodes.activeCount === summary.nodes.totalCount

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <KpiTile
        icon={RadioIcon}
        label="Network Health"
        value={`${summary.nodes.activeCount} / ${summary.nodes.totalCount}`}
        badge={allNodesOnline ? "Optimal" : "Degraded"}
        chips={[
          { label: "Nodes online" },
          { label: `${summary.nodes.totalCapacityKw.toLocaleString()} kW capacity`, tone: "muted" },
        ]}
      />
      <KpiTile
        icon={BatteryChargingIcon}
        label="Storage Availability"
        value={`${summary.batterySlots.available} / ${summary.batterySlots.total}`}
        badge={
          summary.batterySlots.total > 0
            ? `${Math.round((summary.batterySlots.available / summary.batterySlots.total) * 100)}% avail`
            : undefined
        }
        chips={[
          { label: `${summary.batterySlots.available} free` },
          { label: `${summary.batterySlots.reserved} reserved`, tone: "amber" },
          { label: `${summary.batterySlots.occupied} occupied`, tone: "muted" },
        ]}
      />
      <KpiTile
        icon={CalendarClockIcon}
        label="Today's Dispatch"
        value={String(summary.reservations.totalToday)}
        unit="reservations"
        badge={summary.reservations.peakHour !== null ? `Peak ${formatHour(summary.reservations.peakHour)}` : undefined}
        chips={[
          { label: `${summary.reservations.activeNowCount} active now` },
          { label: `${summary.reservations.scheduledTodayCount} scheduled`, tone: "muted" },
        ]}
      />
      <KpiTile
        icon={UsersIcon}
        label="Prosumer Trading"
        value={String(summary.prosumers.active)}
        unit="active"
        badge={summary.prosumers.invited > 0 ? `${summary.prosumers.invited} pending` : undefined}
        chips={[
          {
            label: `${summary.prosumers.invited + summary.prosumers.active + summary.prosumers.deactivated} total registered`,
            tone: "muted",
          },
        ]}
      />
      {isBackoffice ? <StaffCountTile /> : null}
    </div>
  )
}
