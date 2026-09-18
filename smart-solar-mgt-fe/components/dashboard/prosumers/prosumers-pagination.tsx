"use client"

import type { MouseEvent } from "react"

import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"

interface ProsumersPaginationProps {
  page: number
  pageSize: number
  totalCount: number
  onPageChange: (page: number) => void
}

// simple previous/next pager (no numbered links) — this list is backend-paginated but not
// deep-linked, so a "Page X of Y" indicator is enough
export function ProsumersPagination({
  page,
  pageSize,
  totalCount,
  onPageChange,
}: ProsumersPaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))
  const canGoPrevious = page > 1
  const canGoNext = page < totalPages

  const handleClick = (next: number, allowed: boolean) => (event: MouseEvent) => {
    event.preventDefault()
    if (allowed) {
      onPageChange(next)
    }
  }

  if (totalPages <= 1) {
    return null
  }

  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-sm text-muted-foreground">
        Page {page} of {totalPages} · {totalCount} prosumer{totalCount === 1 ? "" : "s"}
      </span>
      <Pagination className="mx-0 w-auto">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              href="#"
              onClick={handleClick(page - 1, canGoPrevious)}
              aria-disabled={!canGoPrevious}
              className={!canGoPrevious ? "pointer-events-none opacity-50" : undefined}
            />
          </PaginationItem>
          <PaginationItem>
            <PaginationNext
              href="#"
              onClick={handleClick(page + 1, canGoNext)}
              aria-disabled={!canGoNext}
              className={!canGoNext ? "pointer-events-none opacity-50" : undefined}
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  )
}
