import { ContentPanel } from "@/components/dashboard/content-panel"

export default function DashboardPage() {
  return (
    <>
      <ContentPanel
        title="Grid Overview"
        description="Energy trading activity across the microgrid"
        className="min-h-72"
      />
      <div className="grid gap-6 md:grid-cols-2">
        <ContentPanel title="Active Reservations" />
        <ContentPanel title="Battery Slot Availability" />
      </div>
    </>
  )
}
