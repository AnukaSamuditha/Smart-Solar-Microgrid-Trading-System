import { useMutation, useQueryClient } from "@tanstack/react-query"

import { logout } from "@/lib/api/auth"

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear()
    },
  })
}
