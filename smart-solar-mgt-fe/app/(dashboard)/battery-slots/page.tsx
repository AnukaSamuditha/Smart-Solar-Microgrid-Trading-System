import { ContentPanel } from "@/components/dashboard/content-panel"

export default function BatterySlotsPage() {
  return (
    <ContentPanel
      title="Battery Slots"
      description="Available battery storage capacity per grid node"
      className="flex-1"
    />
  )
}
