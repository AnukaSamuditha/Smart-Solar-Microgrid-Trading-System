import { apiClient } from "@/providers/api-client"

export interface CurrentUser {
  id: string
  email: string
  username: string | null
  role: "Backoffice" | "GridOperator"
  status: "Invited" | "Active" | "Deactivated"
}

// the profile of the currently authenticated user, resolved from the access token cookie/header
export async function getMe(): Promise<CurrentUser> {
  const { data } = await apiClient.get<CurrentUser>("/api/v1/users/me")
  return data
}
