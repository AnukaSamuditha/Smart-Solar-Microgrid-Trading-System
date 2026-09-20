import { ActiveHubNodesTable } from "@/components/dashboard/overview/active-hub-nodes-table"
import { BatterySlotDistribution } from "@/components/dashboard/overview/battery-slot-distribution"
import { HourlyDispatchChart } from "@/components/dashboard/overview/hourly-dispatch-chart"
import { KpiStrip } from "@/components/dashboard/overview/kpi-strip"
import { RecentActivityFeed } from "@/components/dashboard/overview/recent-activity-feed"

// platform-status landing page for Backoffice and Grid Operator staff — one shared view, no
// per-role tabs. See smart-solar-mgt-fe/docs/dashboard-insights-implementation-plan.md for the
// widget catalog and the decisions behind each widget's data source.
export default function DashboardPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Current status of the microgrid trading platform
        </p>
      </div>

      <KpiStrip />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <ActiveHubNodesTable />
          <HourlyDispatchChart />
        </div>
        <div className="flex flex-col gap-6">
          <BatterySlotDistribution />
          <RecentActivityFeed />
        </div>
      </div>
    </div>
  )
}
