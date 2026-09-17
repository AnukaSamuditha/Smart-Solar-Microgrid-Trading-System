import { useMutation } from "@tanstack/react-query"

import { acceptInvitation } from "@/lib/api/auth"

export function useAcceptInvitation() {
  return useMutation({
    mutationFn: acceptInvitation,
  })
}
