import { useMutation, useQueryClient } from "@tanstack/react-query"

import { login } from "@/lib/api/auth"

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] })
    },
  })
}
