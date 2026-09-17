import { Badge } from "@/components/ui/badge"
import type { UserStatus } from "@/lib/api/users"

const STATUS_LABELS: Record<UserStatus, string> = {
  Active: "Active",
  Invited: "Invited",
  Deactivated: "Deactivated",
}

const STATUS_VARIANTS: Record<UserStatus, "default" | "secondary" | "destructive"> = {
  Active: "default",
  Invited: "secondary",
  Deactivated: "destructive",
}

// flags accounts that haven't completed invitation setup or have been deactivated
export function StatusBadge({ status }: { status: UserStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{STATUS_LABELS[status]}</Badge>
}
