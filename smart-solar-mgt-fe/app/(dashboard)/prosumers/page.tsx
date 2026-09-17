import { ContentPanel } from "@/components/dashboard/content-panel"
import { RequireRole } from "@/components/dashboard/require-role"

export default function ProsumersPage() {
  return (
    <RequireRole roles={["Backoffice"]}>
      <ContentPanel
        title="Prosumers"
        description="Manage prosumer profiles by National Identity Card (NIC)"
        className="flex-1"
      />
    </RequireRole>
  )
}
