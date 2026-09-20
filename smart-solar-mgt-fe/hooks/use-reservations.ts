import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import {
  approveReservation,
  cancelReservation,
  createReservation,
  getReservation,
  listReservations,
  rejectReservation,
  updateReservation,
  type ListReservationsParams,
} from "@/lib/api/reservations"

// all filters live in the query key so each combination caches independently; keepPreviousData
// avoids a loading flash when paging or changing filters
export function useReservations(params: ListReservationsParams) {
  return useQuery({
    queryKey: ["reservations", params],
    queryFn: () => listReservations(params),
    placeholderData: keepPreviousData,
  })
}

export function useReservation(id: string | undefined) {
  return useQuery({
    queryKey: ["reservations", "detail", id],
    queryFn: () => getReservation(id as string),
    enabled: !!id,
  })
}

// invalidates both reservations and nodes: creating/cancelling a reservation also changes the
// target node's battery-slot cached status (see ReservationService in the backend)
export function useCreateReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createReservation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reservations"] })
      queryClient.invalidateQueries({ queryKey: ["nodes"] })
    },
  })
}

export function useUpdateReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateReservation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reservations"] }),
  })
}

export function useCancelReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: cancelReservation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reservations"] })
      queryClient.invalidateQueries({ queryKey: ["nodes"] })
    },
  })
}

// approving also changes the target node's cached battery-slot status (Reserved) - see
// useCreateReservation's identical note
export function useApproveReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: approveReservation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reservations"] })
      queryClient.invalidateQueries({ queryKey: ["nodes"] })
    },
  })
}

export function useRejectReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: rejectReservation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reservations"] }),
  })
}
