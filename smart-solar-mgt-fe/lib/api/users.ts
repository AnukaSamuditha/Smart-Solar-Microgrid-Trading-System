import { apiClient } from "@/providers/api-client"

export type UserRole = "Backoffice" | "GridOperator"
export type UserStatus = "Invited" | "Active" | "Deactivated"

export interface CurrentUser {
  id: string
  email: string
  username: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
  lastLoginAt: string | null
}

// the profile of the currently authenticated user, resolved from the access token cookie/header
export async function getMe(): Promise<CurrentUser> {
  const { data } = await apiClient.get<CurrentUser>("/api/v1/users/me")
  return data
}

export type UserListItem = CurrentUser

// every Backoffice and Grid Operator account (Backoffice-only)
export async function listUsers(): Promise<UserListItem[]> {
  const { data } = await apiClient.get<UserListItem[]>("/api/v1/users/")
  return data
}

export interface CreateUserPayload {
  role: UserRole
  email: string
  username?: string
}

// creates an invited account and sends it a setup invitation email (Backoffice-only)
export async function createUser(payload: CreateUserPayload): Promise<UserListItem> {
  const path = payload.role === "Backoffice" ? "/api/v1/users/backoffice" : "/api/v1/users/grid-operators"
  const { data } = await apiClient.post<UserListItem>(path, {
    email: payload.email,
    username: payload.username,
  })
  return data
}

export interface UpdateUserPayload {
  id: string
  email: string
  username?: string
}

// updates an account's email/username; role and status are immutable via this endpoint (Backoffice-only)
export async function updateUser({ id, ...body }: UpdateUserPayload): Promise<UserListItem> {
  const { data } = await apiClient.put<UserListItem>(`/api/v1/users/${id}`, body)
  return data
}

// permanently deletes an account, cascading its refresh tokens/invitations (Backoffice-only)
export async function deleteUser(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/users/${id}`)
}

// blocks further logins for an account without deleting it (Backoffice-only)
export async function deactivateUser(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/users/${id}/deactivate`)
}

// restores a deactivated account (Backoffice-only)
export async function reactivateUser(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/users/${id}/reactivate`)
}
