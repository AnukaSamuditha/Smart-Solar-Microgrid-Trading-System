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
  InvalidCredentials: "Invalid email or password.",
  ProfileIncomplete:
    "This account hasn't finished setup yet. Check your email for the invitation link, or ask an administrator to resend it.",
  AccountDeactivated: "This account has been deactivated. Contact an administrator.",
  ProsumerMobileOnly: "Prosumer accounts sign in through the Wattex mobile app, not this dashboard.",
  EmailAlreadyInUse: "That email address is already in use.",
  SeededAdminProtected: "This account is protected and can't be deactivated or deleted.",
  ValidEmailRequired: "Enter a valid email address.",
  InvalidNic: "Enter a valid NIC number.",
  NicAlreadyInUse: "A prosumer with that NIC already exists.",
  InvalidStatusFilter: "Invalid status filter.",
  NameRequired: "Enter a name for this node.",
  InvalidLatitude: "Latitude must be between -90 and 90.",
  InvalidLongitude: "Longitude must be between -180 and 180.",
  CapacityMustBePositive: "Capacity must be greater than zero.",
  BatterySlotCountMustNotBeNegative: "Battery slot count can't be negative.",
  OperatingStartTimeMustBeBeforeEndTime: "Operating start time must be before the end time.",
  OpenAndCloseTimeRequiredWhenNotClosed: "Enter an open and close time for every open day, or mark it as closed.",
  OpenTimeMustBeBeforeCloseTime: "Open time must be before close time.",
  SlotNotFound: "That battery slot no longer exists.",
  ActiveReservationsExist: "This node can't be deactivated while it has active energy reservations.",
  NodeIdRequired: "Select a grid node.",
  SlotIdRequired: "Select a battery slot.",
  StartTimeMustBeBeforeEndTime: "Start time must be before the end time.",
  ReservationMustBeInFuture: "Start time must be in the future.",
  ReservationMustBeWithinSevenDays: "Reservations must be scheduled within 7 days.",
  ProsumerNotFound: "No prosumer was found with that NIC.",
  ProsumerDeactivated: "This prosumer's profile is deactivated and can't be booked.",
  NodeNotFound: "That grid node no longer exists.",
  NodeDeactivated: "This grid node is deactivated and can't accept new reservations.",
  SlotNotAvailable: "That battery slot already has an active reservation.",
  NotPendingApproval: "This request has already been reviewed.",
  AlreadyCancelled: "This reservation has already been cancelled.",
  AlreadyStarted: "This reservation's time slot has already started and can no longer be changed.",
  InsufficientNotice: "Changes and cancellations require at least 12 hours' notice.",
  NotPending: "This reservation has already been reviewed.",
  SlotNoLongerAvailable: "This slot was claimed by another reservation in the meantime.",
}

const FALLBACK_MESSAGE = "Something went wrong. Please try again."

// normalize an axios (or unknown) error into a plain ApiError with a user-facing message. Passed
// through unchanged if it's already an ApiError — apiClient's response interceptor
// (providers/api-client.ts) already runs every rejected request through this function, so a
// dialog's own onError handler calling toApiError(error) again must not re-wrap and lose the
// original status/code/message.
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

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
