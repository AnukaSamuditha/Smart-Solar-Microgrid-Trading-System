import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import {
  createNode,
  deactivateNode,
  getNode,
  listNodes,
  reactivateNode,
  updateBatterySlotStatus,
  updateNodeSchedule,
  type ListNodesParams,
} from "@/lib/api/nodes"

// search/status/page/pageSize all live in the query key so each filter combination caches
// independently; keepPreviousData avoids a loading flash when paging or changing filters
export function useNodes(params: ListNodesParams) {
  return useQuery({
    queryKey: ["nodes", params],
    queryFn: () => listNodes(params),
    placeholderData: keepPreviousData,
  })
}

export function useNode(id: string | undefined) {
  return useQuery({
    queryKey: ["nodes", "detail", id],
    queryFn: () => getNode(id as string),
    enabled: !!id,
  })
}

export function useCreateNode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createNode,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["nodes"] }),
  })
}

export function useUpdateNodeSchedule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateNodeSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["nodes"] }),
  })
}

export function useUpdateBatterySlotStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateBatterySlotStatus,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["nodes"] }),
  })
}

export function useDeactivateNode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deactivateNode,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["nodes"] }),
  })
}

export function useReactivateNode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: reactivateNode,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["nodes"] }),
  })
}
