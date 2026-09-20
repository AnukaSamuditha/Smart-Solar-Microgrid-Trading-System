import { Badge } from "@/components/ui/badge"
import type { ProsumerStatus } from "@/lib/api/prosumers"

const STATUS_VARIANTS: Record<ProsumerStatus, "default" | "secondary" | "destructive" | "outline"> = {
  Invited: "secondary",
  Active: "default",
  Deactivated: "destructive",
  PendingApproval: "outline",
  Rejected: "destructive",
}

const STATUS_LABELS: Record<ProsumerStatus, string> = {
  Invited: "Invited",
  Active: "Active",
  Deactivated: "Deactivated",
  PendingApproval: "Pending approval",
  Rejected: "Rejected",
}

// flags profiles that haven't accepted their setup invitation yet or have been deactivated
export function ProsumerStatusBadge({ status }: { status: ProsumerStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{STATUS_LABELS[status]}</Badge>
}
