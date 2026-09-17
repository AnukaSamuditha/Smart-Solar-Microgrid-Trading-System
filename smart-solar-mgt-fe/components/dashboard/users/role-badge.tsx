import { Badge } from "@/components/ui/badge"
import { roleLabel } from "@/lib/nav-config"
import type { UserRole } from "@/lib/api/users"

// visually distinguishes Backoffice (broad access) from Grid Operator (operational-only) accounts
export function RoleBadge({ role }: { role: UserRole }) {
  return (
    <Badge variant={role === "Backoffice" ? "default" : "outline"}>
      {roleLabel(role)}
    </Badge>
  )
}
