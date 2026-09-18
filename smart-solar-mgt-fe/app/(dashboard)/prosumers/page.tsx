"use client"

import { useState } from "react"
import { PlusIcon, SearchIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { CreateProsumerDialog } from "@/components/dashboard/prosumers/create-prosumer-dialog"
import { ProsumersPagination } from "@/components/dashboard/prosumers/prosumers-pagination"
import { ProsumersTable } from "@/components/dashboard/prosumers/prosumers-table"
import { RequireRole } from "@/components/dashboard/require-role"
import { useDebouncedValue } from "@/hooks/use-debounce"
import { useProsumers } from "@/hooks/use-prosumers"
import type { ProsumerStatus } from "@/lib/api/prosumers"

const PAGE_SIZE = 10

type StatusFilter = ProsumerStatus | "All"

export default function ProsumersPage() {
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("All")
  const [page, setPage] = useState(1)
  const [createOpen, setCreateOpen] = useState(false)

  // wait for a pause in typing before the search term reaches the backend request
  const debouncedSearch = useDebouncedValue(search, 300)

  const { data, isPending, isPlaceholderData } = useProsumers({
    search: debouncedSearch.trim() || undefined,
    status: statusFilter === "All" ? undefined : statusFilter,
    page,
    pageSize: PAGE_SIZE,
    sortBy: "CreatedAt",
    sortDir: "desc",
  })

  return (
    <RequireRole roles={["Backoffice", "GridOperator"]}>
      <div className="flex flex-1 flex-col gap-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">Prosumers</h1>
            <p className="text-sm text-muted-foreground">
              Manage prosumer profiles by National Identity Card (NIC)
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            Add prosumer
          </Button>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <span className="text-sm text-muted-foreground">
            {data?.totalCount ?? 0} prosumer{data?.totalCount === 1 ? "" : "s"}
          </span>
          <div className="flex items-center gap-2">
            <div className="relative w-full max-w-xs">
              <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by NIC, name, or email..."
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
                setStatusFilter(value as StatusFilter)
                setPage(1)
              }}
            >
              <SelectTrigger className="w-36">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="All">All statuses</SelectItem>
                <SelectItem value="Invited">Invited</SelectItem>
                <SelectItem value="Active">Active</SelectItem>
                <SelectItem value="Deactivated">Deactivated</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {isPending ? (
          <p className="py-10 text-center text-sm text-muted-foreground">Loading prosumers...</p>
        ) : (
          <div className={isPlaceholderData ? "opacity-60" : undefined}>
            <ProsumersTable prosumers={data?.items ?? []} />
          </div>
        )}

        <ProsumersPagination
          page={data?.page ?? page}
          pageSize={PAGE_SIZE}
          totalCount={data?.totalCount ?? 0}
          onPageChange={setPage}
        />

        <CreateProsumerDialog open={createOpen} onOpenChange={setCreateOpen} />
      </div>
    </RequireRole>
  )
}
