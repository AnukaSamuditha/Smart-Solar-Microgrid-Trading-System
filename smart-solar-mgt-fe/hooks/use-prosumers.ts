import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import {
  createProsumer,
  deactivateProsumer,
  listProsumers,
  reactivateProsumer,
  updateProsumer,
  type ListProsumersParams,
} from "@/lib/api/prosumers"

// search/status/page/pageSize all live in the query key so each filter combination caches
// independently; keepPreviousData avoids a loading flash when paging or changing filters
export function useProsumers(params: ListProsumersParams) {
  return useQuery({
    queryKey: ["prosumers", params],
    queryFn: () => listProsumers(params),
    placeholderData: keepPreviousData,
  })
}

export function useCreateProsumer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createProsumer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["prosumers"] }),
  })
}

export function useUpdateProsumer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateProsumer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["prosumers"] }),
  })
}

export function useDeactivateProsumer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deactivateProsumer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["prosumers"] }),
  })
}

export function useReactivateProsumer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: reactivateProsumer,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["prosumers"] }),
  })
}
