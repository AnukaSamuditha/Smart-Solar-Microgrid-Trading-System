import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { ProsumerListItem } from "@/lib/api/prosumers"

import { ProsumerRowActions } from "./prosumer-row-actions"
import { ProsumerStatusBadge } from "./prosumer-status-badge"

function formatDate(value: string | null): string {
  if (!value) {
    return "Never"
  }
  return new Date(value).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  })
}

export function ProsumersTable({ prosumers }: { prosumers: ProsumerListItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>NIC</TableHead>
          <TableHead>Prosumer</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Date added</TableHead>
          <TableHead>Last updated</TableHead>
          <TableHead className="w-10" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {prosumers.length === 0 ? (
          <TableRow>
            <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
              No prosumers found.
            </TableCell>
          </TableRow>
        ) : (
          prosumers.map((prosumer) => (
            <TableRow key={prosumer.nic}>
              <TableCell className="font-medium">{prosumer.nic}</TableCell>
              <TableCell>
                <div className="flex flex-col">
                  <span className="font-medium">{prosumer.fullName ?? "—"}</span>
                  <span className="text-xs text-muted-foreground">{prosumer.email}</span>
                </div>
              </TableCell>
              <TableCell>
                <ProsumerStatusBadge status={prosumer.status} />
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDate(prosumer.createdAt)}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDate(prosumer.updatedAt)}
              </TableCell>
              <TableCell>
                <ProsumerRowActions prosumer={prosumer} />
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}
