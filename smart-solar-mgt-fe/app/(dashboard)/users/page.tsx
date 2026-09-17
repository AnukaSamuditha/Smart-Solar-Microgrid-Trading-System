"use client"

import { useMemo, useState } from "react"
import { PlusIcon, SearchIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { CreateUserDialog } from "@/components/dashboard/users/create-user-dialog"
import { UsersTable } from "@/components/dashboard/users/users-table"
import { RequireRole } from "@/components/dashboard/require-role"
import { useUsers } from "@/hooks/use-users"

export default function UsersPage() {
  const { data: users, isPending } = useUsers()
  const [search, setSearch] = useState("")
  const [createOpen, setCreateOpen] = useState(false)

  const filteredUsers = useMemo(() => {
    if (!users) return []
    const query = search.trim().toLowerCase()
    if (!query) return users
    return users.filter(
      (user) =>
        user.email.toLowerCase().includes(query) ||
        (user.username?.toLowerCase().includes(query) ?? false)
    )
  }, [users, search])

  return (
    <RequireRole roles={["Backoffice"]}>
      <div className="flex flex-1 flex-col gap-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">User Management</h1>
            <p className="text-sm text-muted-foreground">
              Create and manage Backoffice and Grid Operator accounts
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            Add user
          </Button>
        </div>

        <div className="flex items-center justify-between gap-4">
          <span className="text-sm text-muted-foreground">
            All users {users?.length ?? 0}
          </span>
          <div className="relative w-full max-w-xs">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search users..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8"
            />
          </div>
        </div>

        {isPending ? (
          <p className="py-10 text-center text-sm text-muted-foreground">Loading users...</p>
        ) : (
          <UsersTable users={filteredUsers} />
        )}

        <CreateUserDialog open={createOpen} onOpenChange={setCreateOpen} />
      </div>
    </RequireRole>
  )
}
