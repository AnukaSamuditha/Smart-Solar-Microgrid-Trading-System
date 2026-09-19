import { Badge } from "@/components/ui/badge"
import type { MicrogridNodeStatus } from "@/lib/api/nodes"

const STATUS_VARIANTS: Record<MicrogridNodeStatus, "default" | "destructive"> = {
  Active: "default",
  Deactivated: "destructive",
}

export function NodeStatusBadge({ status }: { status: MicrogridNodeStatus }) {
  return <Badge variant={STATUS_VARIANTS[status]}>{status}</Badge>
}
