import { isAxiosError } from "axios"

export class ApiError extends Error {
  status: number
  code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.code = code
  }
}

// backend error codes (see smart-solar-mgt-api Endpoints/*.cs) mapped to user-facing copy
const ERROR_MESSAGES: Record<string, string> = {
  EmailAndPasswordRequired: "Enter both your email and password.",
  InvalidRequest: "This link is invalid. Request a new invitation.",
  NotFound: "This link is invalid or has expired.",
  Expired: "This link has expired. Request a new invitation.",
  AlreadyUsed: "This link has already been used.",
  CsrfValidationFailed: "Your session has expired. Please refresh the page and try again.",
}

const FALLBACK_MESSAGE = "Something went wrong. Please try again."

// normalize an axios (or unknown) error into a plain ApiError with a user-facing message
export function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    const status = error.response?.status ?? 0
    const code = (error.response?.data as { error?: string } | undefined)?.error

    if (status === 401 && !code) {
      return new ApiError(status, "Invalid email or password.", code)
    }

    const message = (code && ERROR_MESSAGES[code]) ?? FALLBACK_MESSAGE
    return new ApiError(status, message, code)
  }

  return new ApiError(0, FALLBACK_MESSAGE)
}
