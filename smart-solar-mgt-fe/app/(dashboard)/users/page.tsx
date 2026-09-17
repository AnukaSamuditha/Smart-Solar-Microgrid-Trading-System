import { ContentPanel } from "@/components/dashboard/content-panel"
import { RequireRole } from "@/components/dashboard/require-role"

export default function UsersPage() {
  return (
    <RequireRole roles={["Backoffice"]}>
      <ContentPanel
        title="User Management"
        description="Create and manage Backoffice and Grid Operator accounts"
        className="flex-1"
      />
    </RequireRole>
  )
}
