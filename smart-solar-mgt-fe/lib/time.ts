// "HH:mm:ss" (the API's TimeOnly format) <-> "HH:mm" (what <input type="time"> expects). Shared
// by the node schedule editor and the create-node dialog's operating-hours fields.

export function toInputTime(value: string | null): string {
  return value ? value.slice(0, 5) : ""
}

export function toApiTime(value: string): string | null {
  return value ? `${value}:00` : null
}

// UTC ISO 8601 instant (System.Text.Json parses this into DateTimeKind.Utc) <-> the local
// "yyyy-MM-ddTHH:mm" string <input type="datetime-local"> expects. Unlike toApiTime/toInputTime
// above (TimeOnly-only, no timezone), these convert through a real Date so the reservation's
// StartTime/EndTime round-trip correctly regardless of the viewer's local timezone.
export function toInputDateTime(value: string | null): string {
  if (!value) {
    return ""
  }
  const date = new Date(value)
  const pad = (n: number) => n.toString().padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toApiDateTime(value: string): string | null {
  if (!value) {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}
