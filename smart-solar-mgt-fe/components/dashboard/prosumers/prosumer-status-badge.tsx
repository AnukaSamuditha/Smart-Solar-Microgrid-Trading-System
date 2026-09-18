import { Badge } from "@/components/ui/badge"
import type { ProsumerStatus } from "@/lib/api/prosumers"

const STATUS_VARIANTS: Record<ProsumerStatus, "default" | "secondary" | "destructive"> = {
  Invited: "secondary",
  Active: "default",
  Deactivated: "destructive",
}

// flags profiles that haven't accepted their setup invitation yet or have been deactivated
export function ProsumerStatusBadge({ status }: { status: ProsumerStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{status}</Badge>
}
