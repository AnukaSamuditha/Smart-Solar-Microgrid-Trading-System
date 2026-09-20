import { Badge } from "@/components/ui/badge"
import type { ReservationStatus } from "@/lib/api/reservations"

const STATUS_VARIANTS: Record<ReservationStatus, "default" | "secondary" | "destructive" | "outline"> = {
  Pending: "outline",
  Confirmed: "default",
  Rejected: "destructive",
  Cancelled: "destructive",
  Completed: "secondary",
}

export function ReservationStatusBadge({ status }: { status: ReservationStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{status}</Badge>
}
