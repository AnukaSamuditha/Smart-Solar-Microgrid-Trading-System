import { ContentPanel } from "@/components/dashboard/content-panel"
import { RequireRole } from "@/components/dashboard/require-role"

export default function GridNodesPage() {
  return (
    <RequireRole roles={["Backoffice"]}>
      <ContentPanel
        title="All Nodes"
        description="Solar grid hubs: location, capacity, and battery storage slots"
        className="flex-1"
      />
    </RequireRole>
  )
}
