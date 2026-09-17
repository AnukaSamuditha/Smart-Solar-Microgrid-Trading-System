import { ContentPanel } from "@/components/dashboard/content-panel"
import { RequireRole } from "@/components/dashboard/require-role"

export default function NodeSchedulesPage() {
  return (
    <RequireRole roles={["Backoffice"]}>
      <ContentPanel
        title="Node Schedules"
        description="Operational schedules for solar grid hubs"
        className="flex-1"
      />
    </RequireRole>
  )
}
