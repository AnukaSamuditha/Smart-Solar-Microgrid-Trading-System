"use client"

import { useMemo, useState } from "react"
import { SearchIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { BatterySlotStatus, MicrogridNode } from "@/lib/api/nodes"

import { BatterySlotStatusControl } from "./battery-slot-status-control"

const PAGE_SIZE = 10

type StatusFilter = BatterySlotStatus | "All"

interface BatterySlotsSheetProps {
  node: MicrogridNode | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

// the per-node drill-down: search + status filter + a paginated table, so a node with hundreds
// of slots stays scannable — the overview (BatterySlotsPanel) never renders more than one
// SlotCapacityBar per node, and this is the only place individual slots are listed
function BatterySlotsSheetContent({ node }: { node: MicrogridNode }) {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("All")
  const [page, setPage] = useState(1)

  const filteredSlots = useMemo(() => {
    return node.batterySlots.filter((slot) => {
      if (statusFilter !== "All" && slot.status !== statusFilter) {
        return false
      }
      if (search.trim() && !slot.slotId.includes(search.trim())) {
        return false
      }
      return true
    })
  }, [node.batterySlots, statusFilter, search])

  const totalPages = Math.max(1, Math.ceil(filteredSlots.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const pageSlots = filteredSlots.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)
  const availableCount = node.batterySlots.filter((s) => s.status === "Available").length

  return (
    <>
      <SheetHeader>
        <SheetTitle>{node.name}</SheetTitle>
        <SheetDescription>
          {availableCount}/{node.batterySlots.length} available &middot; {node.batterySlots.length}{" "}
          total slots
        </SheetDescription>
      </SheetHeader>

      {/* pt-2 gives the search input's focus ring room to render instead of being clipped
          flush against this scroll container's top edge */}
      <div className="flex flex-1 flex-col gap-3 overflow-y-auto px-4 pt-2 pb-4">
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by slot ID..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(1)
              }}
              className="pl-8"
            />
          </div>
          <Select
            value={statusFilter}
            onValueChange={(value) => {
              setStatusFilter((value ?? "All") as StatusFilter)
              setPage(1)
            }}
          >
            <SelectTrigger className="w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="All">All statuses</SelectItem>
              <SelectItem value="Available">Available</SelectItem>
              <SelectItem value="Reserved">Reserved</SelectItem>
              <SelectItem value="Occupied">Occupied</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Slot</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pageSlots.length === 0 ? (
              <TableRow>
                <TableCell colSpan={2} className="h-24 text-center text-muted-foreground">
                  No slots match this filter.
                </TableCell>
              </TableRow>
            ) : (
              pageSlots.map((slot) => (
                <TableRow key={slot.slotId}>
                  <TableCell className="font-medium">Slot {slot.slotId}</TableCell>
                  <TableCell>
                    <BatterySlotStatusControl node={node} slot={slot} />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        {totalPages > 1 ? (
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              Page {safePage} of {totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={safePage <= 1}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={safePage >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </>
  )
}

export function BatterySlotsSheet({ node, open, onOpenChange }: BatterySlotsSheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="flex flex-col gap-4 sm:max-w-2xl">
        {/* keyed on node id so search/filter/page state resets when switching nodes */}
        {node ? <BatterySlotsSheetContent key={node.id} node={node} /> : null}
      </SheetContent>
    </Sheet>
  )
}
