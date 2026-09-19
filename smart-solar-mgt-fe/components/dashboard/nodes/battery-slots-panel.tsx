"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { MicrogridNode } from "@/lib/api/nodes"

import { BatterySlotsSheet } from "./battery-slots-sheet"
import { SlotCapacityBar } from "./slot-capacity-bar"

// Grid Operators' day-to-day tool (Backoffice can also use it) for updating individual battery
// slot availability, per project-specification.md section 6. The overview shows only an
// at-a-glance proportional bar per node (SlotCapacityBar) — never one control per slot — so it
// stays readable at any slot count; per-slot status changes happen in the searchable, paginated
// BatterySlotsSheet opened via "Manage slots".
export function BatterySlotsPanel({ nodes }: { nodes: MicrogridNode[] }) {
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const selectedNode = nodes.find((node) => node.id === selectedNodeId) ?? null

  if (nodes.length === 0) {
    return (
      <p className="py-10 text-center text-sm text-muted-foreground">No active grid nodes yet.</p>
    )
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Node</TableHead>
            <TableHead>Battery slots</TableHead>
            <TableHead className="w-36" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {nodes.map((node) => {
            const availableCount = node.batterySlots.filter((s) => s.status === "Available").length

            return (
              <TableRow key={node.id}>
                <TableCell className="font-medium">{node.name}</TableCell>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <SlotCapacityBar slots={node.batterySlots} className="w-40" />
                    <span className="text-sm whitespace-nowrap text-muted-foreground">
                      {availableCount}/{node.batterySlots.length} available
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <Button variant="outline" size="sm" onClick={() => setSelectedNodeId(node.id)}>
                    Manage slots
                  </Button>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      <BatterySlotsSheet
        node={selectedNode}
        open={!!selectedNodeId}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) {
            setSelectedNodeId(null)
          }
        }}
      />
    </>
  )
}
