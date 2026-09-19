import type { BatterySlotStatus } from "@/lib/api/nodes"

export const BATTERY_SLOT_STATUS_OPTIONS: BatterySlotStatus[] = ["Available", "Reserved", "Occupied"]

// this app's design tokens only define --primary (lime) and --destructive (red) — --secondary
// sits too close in lightness to --muted in the dark theme to read as a distinct status color
// (see app/globals.css), so Reserved uses a locally-scoped amber utility here instead, matching
// the traffic-light (green/amber/red) convention recommended for inventory/status dashboards
export const BATTERY_SLOT_STATUS_META: Record<
  BatterySlotStatus,
  { barClassName: string; badgeClassName: string }
> = {
  Available: {
    barClassName: "bg-primary",
    badgeClassName: "border-transparent bg-primary text-primary-foreground",
  },
  Reserved: {
    barClassName: "bg-amber-500",
    badgeClassName: "border-transparent bg-amber-500 text-amber-950",
  },
  Occupied: {
    barClassName: "bg-destructive",
    badgeClassName: "border-transparent bg-destructive/10 text-destructive dark:bg-destructive/20",
  },
}
