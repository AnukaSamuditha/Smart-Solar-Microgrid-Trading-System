import { Badge } from "@/components/ui/badge"
import type { ReservationStatus } from "@/lib/api/reservations"

const STATUS_VARIANTS: Record<ReservationStatus, "default" | "destructive"> = {
  Confirmed: "default",
  Cancelled: "destructive",
}

export function ReservationStatusBadge({ status }: { status: ReservationStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{status}</Badge>
}
