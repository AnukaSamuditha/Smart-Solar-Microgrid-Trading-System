"use client"

import { useState } from "react"
import { PlusIcon, SearchIcon } from "lucide-react"

import { CreateReservationDialog } from "@/components/dashboard/reservations/create-reservation-dialog"
import { ReservationsPagination } from "@/components/dashboard/reservations/reservations-pagination"
import { ReservationsTable } from "@/components/dashboard/reservations/reservations-table"
import { RequireRole } from "@/components/dashboard/require-role"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useDebouncedValue } from "@/hooks/use-debounce"
import { useNodes } from "@/hooks/use-nodes"
import { useReservations } from "@/hooks/use-reservations"
import type { ReservationStatus } from "@/lib/api/reservations"
import { toApiDateTime } from "@/lib/time"

const PAGE_SIZE = 10

type StatusFilter = ReservationStatus | "All"

export default function ReservationsPage() {
  const [prosumerNic, setProsumerNic] = useState("")
  const [nodeFilter, setNodeFilter] = useState("All")
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("All")
  const [dateFrom, setDateFrom] = useState("")
  const [dateTo, setDateTo] = useState("")
  const [page, setPage] = useState(1)
  const [createOpen, setCreateOpen] = useState(false)

  // wait for a pause in typing before the NIC filter reaches the backend request
  const debouncedNic = useDebouncedValue(prosumerNic, 300)
  const { data: nodes } = useNodes({ pageSize: 100 })

  // base-ui's <Select.Value> only renders a label if the value is registered in this map — see
  // the identical note in create-reservation-dialog.tsx
  const nodeFilterItems = {
    All: "All nodes",
    ...Object.fromEntries((nodes?.items ?? []).map((node) => [node.id, node.name])),
  }

  const { data, isPending, isPlaceholderData } = useReservations({
    prosumerNic: debouncedNic.trim() || undefined,
    nodeId: nodeFilter === "All" ? undefined : nodeFilter,
    status: statusFilter === "All" ? undefined : statusFilter,
    dateFrom: toApiDateTime(dateFrom) ?? undefined,
    dateTo: toApiDateTime(dateTo) ?? undefined,
    page,
    pageSize: PAGE_SIZE,
    sortBy: "StartTime",
    sortDir: "asc",
  })

  return (
    <RequireRole roles={["Backoffice", "GridOperator"]}>
      <div className="flex flex-1 flex-col gap-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">Reservations</h1>
            <p className="text-sm text-muted-foreground">
              Power trading slot bookings across all grid nodes
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            New reservation
          </Button>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <span className="text-sm text-muted-foreground">
            {data?.totalCount ?? 0} reservation{data?.totalCount === 1 ? "" : "s"}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative w-full max-w-48">
              <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Filter by NIC..."
                value={prosumerNic}
                onChange={(e) => {
                  setProsumerNic(e.target.value)
                  setPage(1)
                }}
                className="pl-8"
              />
            </div>
            <Select
              items={nodeFilterItems}
              value={nodeFilter}
              onValueChange={(value) => {
                setNodeFilter(value ?? "All")
                setPage(1)
              }}
            >
              <SelectTrigger className="w-40">
                <SelectValue placeholder="Grid node" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="All">All nodes</SelectItem>
                {(nodes?.items ?? []).map((node) => (
                  <SelectItem key={node.id} value={node.id}>
                    {node.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={statusFilter}
              onValueChange={(value) => {
                setStatusFilter(value as StatusFilter)
                setPage(1)
              }}
            >
              <SelectTrigger className="w-36">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="All">All statuses</SelectItem>
                <SelectItem value="Pending">Pending</SelectItem>
                <SelectItem value="Confirmed">Confirmed</SelectItem>
                <SelectItem value="Rejected">Rejected</SelectItem>
                <SelectItem value="Cancelled">Cancelled</SelectItem>
                <SelectItem value="Completed">Completed</SelectItem>
              </SelectContent>
            </Select>
            <Input
              type="date"
              aria-label="From date"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value)
                setPage(1)
              }}
              className="w-36"
            />
            <Input
              type="date"
              aria-label="To date"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value)
                setPage(1)
              }}
              className="w-36"
            />
          </div>
        </div>

        {isPending ? (
          <p className="py-10 text-center text-sm text-muted-foreground">Loading reservations...</p>
        ) : (
          <div className={isPlaceholderData ? "opacity-60" : undefined}>
            <ReservationsTable reservations={data?.items ?? []} />
          </div>
        )}

        <ReservationsPagination
          page={data?.page ?? page}
          pageSize={PAGE_SIZE}
          totalCount={data?.totalCount ?? 0}
          onPageChange={setPage}
        />

        <CreateReservationDialog open={createOpen} onOpenChange={setCreateOpen} />
      </div>
    </RequireRole>
  )
}
