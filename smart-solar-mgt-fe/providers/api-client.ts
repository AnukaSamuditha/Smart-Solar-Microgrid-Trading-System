import axios, { type AxiosRequestConfig } from "axios"

import { toApiError } from "@/lib/api/errors"

const CSRF_COOKIE_NAME = "XSRF-TOKEN"
const CSRF_HEADER_NAME = "X-XSRF-TOKEN"
const SAFE_METHODS = new Set(["get", "head", "options"])

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null
  const match = document.cookie.match(
    new RegExp(`(?:^|; )${name.replace(/[.$?*|{}()[\]\\/+^]/g, "\\$&")}=([^;]*)`)
  )
  return match ? decodeURIComponent(match[1]) : null
}

// requests go to this app's own origin and are proxied to the API by the /api/v1/* rewrite in
// next.config.ts, so the browser treats the HttpOnly access/refresh cookies as first-party and
// attaches them automatically
export const apiClient = axios.create()

// echo the readable CSRF cookie back as a header on mutating requests (double-submit pattern)
apiClient.interceptors.request.use((config) => {
  const method = config.method?.toLowerCase()
  if (method && !SAFE_METHODS.has(method)) {
    const csrfToken = readCookie(CSRF_COOKIE_NAME)
    if (csrfToken) {
      config.headers.set(CSRF_HEADER_NAME, csrfToken)
    }
  }
  return config
})

type RetryableConfig = AxiosRequestConfig & { _retry?: boolean }

// coalesce concurrent 401s onto a single in-flight refresh call instead of firing one each
let refreshPromise: Promise<unknown> | null = null

// on a 401 from any non-auth endpoint, attempt one silent refresh (via the refresh_token
// cookie) and retry the original request once; otherwise normalize the error and reject
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config as RetryableConfig | undefined
    const isAuthEndpoint = originalRequest?.url?.includes("/api/v1/auth/")

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true
      try {
        refreshPromise ??= apiClient.post("/api/v1/auth/refresh").finally(() => {
          refreshPromise = null
        })
        await refreshPromise
        return apiClient(originalRequest)
      } catch {
        return Promise.reject(toApiError(error))
      }
    }

    return Promise.reject(toApiError(error))
  }
)
