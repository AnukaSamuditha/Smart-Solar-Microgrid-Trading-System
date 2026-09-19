import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { Reservation } from "@/lib/api/reservations"

import { ReservationRowActions } from "./reservation-row-actions"
import { ReservationStatusBadge } from "./reservation-status-badge"

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  })
}

export function ReservationsTable({ reservations }: { reservations: Reservation[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Prosumer</TableHead>
          <TableHead>Grid node</TableHead>
          <TableHead>Slot</TableHead>
          <TableHead>Start</TableHead>
          <TableHead>End</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="w-10" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {reservations.length === 0 ? (
          <TableRow>
            <TableCell colSpan={7} className="h-24 text-center text-muted-foreground">
              No reservations found.
            </TableCell>
          </TableRow>
        ) : (
          reservations.map((reservation) => (
            <TableRow key={reservation.id}>
              <TableCell>
                <div className="flex flex-col">
                  <span className="font-medium">
                    {reservation.prosumerFullName ?? reservation.prosumerNic}
                  </span>
                  <span className="text-xs text-muted-foreground">{reservation.prosumerNic}</span>
                </div>
              </TableCell>
              <TableCell>{reservation.nodeName ?? reservation.nodeId}</TableCell>
              <TableCell>Slot {reservation.slotId}</TableCell>
              <TableCell className="text-muted-foreground">
                {formatDateTime(reservation.startTime)}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDateTime(reservation.endTime)}
              </TableCell>
              <TableCell>
                <ReservationStatusBadge status={reservation.status} />
              </TableCell>
              <TableCell>
                <ReservationRowActions reservation={reservation} />
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}
