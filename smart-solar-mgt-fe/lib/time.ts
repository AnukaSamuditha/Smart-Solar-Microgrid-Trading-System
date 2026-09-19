// "HH:mm:ss" (the API's TimeOnly format) <-> "HH:mm" (what <input type="time"> expects). Shared
// by the node schedule editor and the create-node dialog's operating-hours fields.

export function toInputTime(value: string | null): string {
  return value ? value.slice(0, 5) : ""
}

export function toApiTime(value: string): string | null {
  return value ? `${value}:00` : null
}
