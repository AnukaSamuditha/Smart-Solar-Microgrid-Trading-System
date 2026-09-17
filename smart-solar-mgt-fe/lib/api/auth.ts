import { apiClient } from "@/providers/api-client"

export interface LoginPayload {
  email: string
  password: string
}

export interface LoginResult {
  role: "Backoffice" | "GridOperator"
}

// the access/refresh tokens are also returned in the body for the mobile client, but the web
// client relies on the HttpOnly cookies the API sets on this same response and ignores them
export async function login(payload: LoginPayload): Promise<LoginResult> {
  const { data } = await apiClient.post("/api/v1/auth/login", payload)
  return { role: data.role }
}

// revokes the current refresh token and clears the auth cookies server-side
export function logout(): Promise<void> {
  return apiClient.post("/api/v1/auth/logout")
}

export interface AcceptInvitationPayload {
  token: string
  newPassword: string
}

// consumes a single-use invitation token (from an account-setup or admin-issued reset link)
// and activates the account with the chosen password
export function acceptInvitation(payload: AcceptInvitationPayload): Promise<void> {
  return apiClient.post("/api/v1/auth/accept-invitation", {
    token: payload.token,
    newPassword: payload.newPassword,
  })
}
