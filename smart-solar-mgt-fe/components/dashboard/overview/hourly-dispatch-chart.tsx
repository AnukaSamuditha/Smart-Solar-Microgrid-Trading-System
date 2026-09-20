"use client"

import { useMemo } from "react"
import { Bar, BarChart, CartesianGrid, XAxis } from "recharts"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Skeleton } from "@/components/ui/skeleton"
import { useTodaysReservations } from "@/hooks/use-dashboard"

import { classifyReservation } from "./reservation-activity"

// business-hours window (06:00–21:00), matching the default node operating-hours convention used
// elsewhere in this app (node-schedule-editor.tsx)
const START_HOUR = 6
const END_HOUR = 21

const chartConfig = {
  completed: { label: "Completed", color: "var(--muted-foreground)" },
  active: { label: "Active now", color: "var(--primary)" },
  scheduled: { label: "Scheduled", color: "var(--color-blue-500)" },
} satisfies ChartConfig

function formatHour(hour: number): string {
  return `${hour.toString().padStart(2, "0")}:00`
}

// today's reservations, bucketed by StartTime hour and by completed/active/scheduled — the same
// classification the Active Hub Nodes table's Reservations column uses, so the two widgets agree
export function HourlyDispatchChart() {
  const { data, isPending } = useTodaysReservations()

  const chartData = useMemo(() => {
    const now = new Date()
    const buckets = new Map<number, { completed: number; active: number; scheduled: number }>()
    for (let hour = START_HOUR; hour <= END_HOUR; hour += 1) {
      buckets.set(hour, { completed: 0, active: 0, scheduled: 0 })
    }

    for (const reservation of data?.items ?? []) {
      const hour = new Date(reservation.startTime).getHours()
      const bucket = buckets.get(hour)
      if (!bucket) continue
      bucket[classifyReservation(reservation, now)] += 1
    }

    return Array.from(buckets.entries()).map(([hour, counts]) => ({
      label: formatHour(hour),
      ...counts,
    }))
  }, [data])

  const totalToday = data?.items.length ?? 0

  return (
    <Card className="flex flex-col gap-0 py-0">
      <CardHeader className="border-b border-border/60 py-4">
        <CardTitle className="text-base">Hourly Dispatch &amp; Reservation Load Profile</CardTitle>
        <CardDescription>
          {totalToday > 0 ? `${totalToday} reservations today, by hour` : "Today's reservations by hour"}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-4">
        {isPending ? (
          <Skeleton className="h-56 w-full rounded-lg" />
        ) : (
          <>
            <ChartContainer config={chartConfig} className="aspect-auto h-56 w-full">
              <BarChart data={chartData}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} interval={1} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Bar dataKey="completed" stackId="hour" fill="var(--color-completed)" radius={[0, 0, 2, 2]} />
                <Bar dataKey="active" stackId="hour" fill="var(--color-active)" />
                <Bar dataKey="scheduled" stackId="hour" fill="var(--color-scheduled)" radius={[2, 2, 0, 0]} />
              </BarChart>
            </ChartContainer>
            <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
              {(Object.keys(chartConfig) as (keyof typeof chartConfig)[]).map((key) => (
                <span key={key} className="flex items-center gap-1.5">
                  <span
                    className="size-2.5 rounded-full"
                    style={{ backgroundColor: chartConfig[key].color }}
                  />
                  {chartConfig[key].label}
                </span>
              ))}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
