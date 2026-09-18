import { apiClient } from "@/providers/api-client"

export type ProsumerStatus = "Invited" | "Active" | "Deactivated"

export interface ProsumerListItem {
  nic: string
  email: string
  fullName: string | null
  status: ProsumerStatus
  createdAt: string
  updatedAt: string | null
}

export interface PagedResult<T> {
  items: T[]
  totalCount: number
  page: number
  pageSize: number
}

export interface ListProsumersParams {
  search?: string
  status?: ProsumerStatus
  page?: number
  pageSize?: number
  sortBy?: "Nic" | "Email" | "CreatedAt"
  sortDir?: "asc" | "desc"
}

// backend-driven search/status filter/sort/pagination (Backoffice or Grid Operator)
export async function listProsumers(
  params: ListProsumersParams
): Promise<PagedResult<ProsumerListItem>> {
  const { data } = await apiClient.get<PagedResult<ProsumerListItem>>("/api/v1/prosumers", {
    params,
  })
  return data
}

export interface CreateProsumerPayload {
  nic: string
  email: string
  fullName?: string
}

// creates an Invited prosumer profile and sends it a setup invitation email, exactly like
// creating a Backoffice/Grid Operator user (see lib/api/users.ts's createUser)
export async function createProsumer(payload: CreateProsumerPayload): Promise<ProsumerListItem> {
  const { data } = await apiClient.post<ProsumerListItem>("/api/v1/prosumers", payload)
  return data
}

export interface UpdateProsumerPayload {
  nic: string
  email: string
  fullName?: string
}

// updates a profile's email/full name; NIC is immutable (Backoffice or Grid Operator)
export async function updateProsumer({
  nic,
  ...body
}: UpdateProsumerPayload): Promise<ProsumerListItem> {
  const { data } = await apiClient.put<ProsumerListItem>(`/api/v1/prosumers/${nic}`, body)
  return data
}

// blocks a profile without deleting it (Backoffice or Grid Operator)
export async function deactivateProsumer(nic: string): Promise<void> {
  await apiClient.patch(`/api/v1/prosumers/${nic}/deactivate`)
}

// restores a deactivated profile (Backoffice-only; Grid Operators get a 403)
export async function reactivateProsumer(nic: string): Promise<void> {
  await apiClient.patch(`/api/v1/prosumers/${nic}/reactivate`)
}
