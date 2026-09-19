import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { MicrogridNode } from "@/lib/api/nodes"

import { NodeRowActions } from "./node-row-actions"
import { NodeStatusBadge } from "./node-status-badge"

function availableSlotCount(node: MicrogridNode): number {
  return node.batterySlots.filter((slot) => slot.status === "Available").length
}

export function NodesTable({ nodes }: { nodes: MicrogridNode[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Location</TableHead>
          <TableHead>Capacity</TableHead>
          <TableHead>Battery slots</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="w-10" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {nodes.length === 0 ? (
          <TableRow>
            <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
              No grid nodes found.
            </TableCell>
          </TableRow>
        ) : (
          nodes.map((node) => (
            <TableRow key={node.id}>
              <TableCell className="font-medium">{node.name}</TableCell>
              <TableCell className="text-muted-foreground">
                {node.latitude.toFixed(4)}, {node.longitude.toFixed(4)}
              </TableCell>
              <TableCell>{node.capacityKw} kW/h</TableCell>
              <TableCell>
                {availableSlotCount(node)}/{node.batterySlots.length} available
              </TableCell>
              <TableCell>
                <NodeStatusBadge status={node.status} />
              </TableCell>
              <TableCell>
                <NodeRowActions node={node} />
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}
