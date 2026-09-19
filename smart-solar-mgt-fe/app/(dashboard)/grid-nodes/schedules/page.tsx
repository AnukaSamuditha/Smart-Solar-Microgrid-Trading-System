"use client"

import { useState } from "react"

import { NodeScheduleEditor } from "@/components/dashboard/nodes/node-schedule-editor"
import { RequireRole } from "@/components/dashboard/require-role"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useNodes } from "@/hooks/use-nodes"

export default function NodeSchedulesPage() {
  const [selectedNodeId, setSelectedNodeId] = useState<string | undefined>(undefined)

  const { data, isPending } = useNodes({
    status: "Active",
    page: 1,
    pageSize: 100,
    sortBy: "Name",
    sortDir: "asc",
  })
  const nodes = data?.items ?? []
  const selectedNode = nodes.find((node) => node.id === selectedNodeId) ?? nodes[0]

  return (
    <RequireRole roles={["Backoffice"]}>
      <div className="flex flex-1 flex-col gap-4">
        <div>
          <h1 className="text-xl font-semibold">Node Schedules</h1>
          <p className="text-sm text-muted-foreground">
            Operational schedules for solar grid hubs
          </p>
        </div>

        {isPending ? (
          <p className="py-10 text-center text-sm text-muted-foreground">Loading nodes...</p>
        ) : nodes.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">
            No active nodes yet. Register one from All Nodes first.
          </p>
        ) : (
          <>
            <Select
              items={nodes.map((node) => ({ value: node.id, label: node.name }))}
              value={selectedNode?.id}
              onValueChange={(value) => setSelectedNodeId(value ?? undefined)}
            >
              <SelectTrigger className="w-64">
                <SelectValue placeholder="Select a node" />
              </SelectTrigger>
              <SelectContent>
                {nodes.map((node) => (
                  <SelectItem key={node.id} value={node.id}>
                    {node.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            {selectedNode ? <NodeScheduleEditor key={selectedNode.id} node={selectedNode} /> : null}
          </>
        )}
      </div>
    </RequireRole>
  )
}
