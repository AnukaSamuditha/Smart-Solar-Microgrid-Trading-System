"use client"

import { useState } from "react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { useUpdateNodeSchedule } from "@/hooks/use-nodes"
import { toApiError } from "@/lib/api/errors"
import type { DayOfWeek, MicrogridNode, ScheduleEntry } from "@/lib/api/nodes"
import { toApiTime, toInputTime } from "@/lib/time"

const DAYS: DayOfWeek[] = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
]

interface DayRow {
  dayOfWeek: DayOfWeek
  openTime: string
  closeTime: string
  isClosed: boolean
}

function buildInitialRows(schedule: ScheduleEntry[]): DayRow[] {
  return DAYS.map((day) => {
    const entry = schedule.find((e) => e.dayOfWeek === day)
    return {
      dayOfWeek: day,
      openTime: toInputTime(entry?.openTime ?? null),
      closeTime: toInputTime(entry?.closeTime ?? null),
      isClosed: entry?.isClosed ?? true,
    }
  })
}

// weekly operating-hours editor for a single node; replaces the node's entire schedule on save
// (Backoffice-only, see app/(dashboard)/grid-nodes/schedules/page.tsx)
export function NodeScheduleEditor({ node }: { node: MicrogridNode }) {
  const [rows, setRows] = useState<DayRow[]>(() => buildInitialRows(node.schedule))
  const [error, setError] = useState<string | null>(null)
  const updateSchedule = useUpdateNodeSchedule()

  const updateRow = (day: DayOfWeek, patch: Partial<DayRow>) => {
    setError(null)
    setRows((prev) => prev.map((row) => (row.dayOfWeek === day ? { ...row, ...patch } : row)))
  }

  const handleSave = () => {
    setError(null)

    for (const row of rows) {
      if (row.isClosed) continue
      if (!row.openTime || !row.closeTime) {
        setError("Enter an open and close time for every open day, or mark it as closed.")
        return
      }
      if (row.openTime >= row.closeTime) {
        setError("Open time must be before close time.")
        return
      }
    }

    const schedule: ScheduleEntry[] = rows.map((row) => ({
      dayOfWeek: row.dayOfWeek,
      openTime: row.isClosed ? null : toApiTime(row.openTime),
      closeTime: row.isClosed ? null : toApiTime(row.closeTime),
      isClosed: row.isClosed,
    }))

    updateSchedule.mutate(
      { id: node.id, schedule },
      {
        onSuccess: () => toast.success(`${node.name}'s schedule updated.`),
        onError: (err) => toast.error(toApiError(err).message),
      }
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        {rows.map((row) => (
          <div
            key={row.dayOfWeek}
            className="flex flex-wrap items-center gap-3 rounded-lg border border-border/60 p-3"
          >
            <label className="flex w-36 items-center gap-2 text-sm font-medium">
              <Checkbox
                checked={!row.isClosed}
                onCheckedChange={(checked) => updateRow(row.dayOfWeek, { isClosed: !checked })}
              />
              {row.dayOfWeek}
            </label>
            <Input
              type="time"
              value={row.openTime}
              disabled={row.isClosed}
              onChange={(e) => updateRow(row.dayOfWeek, { openTime: e.target.value })}
              className="w-32"
            />
            <span className="text-sm text-muted-foreground">to</span>
            <Input
              type="time"
              value={row.closeTime}
              disabled={row.isClosed}
              onChange={(e) => updateRow(row.dayOfWeek, { closeTime: e.target.value })}
              className="w-32"
            />
            {row.isClosed ? <span className="text-sm text-muted-foreground">Closed</span> : null}
          </div>
        ))}
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="flex justify-end">
        <Button onClick={handleSave} disabled={updateSchedule.isPending}>
          {updateSchedule.isPending ? "Saving..." : "Save schedule"}
        </Button>
      </div>
    </div>
  )
}
