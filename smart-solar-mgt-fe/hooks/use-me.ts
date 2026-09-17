import { useQuery } from "@tanstack/react-query"

import { getMe } from "@/lib/api/users"

// bootstraps the current session from the auth cookies; retry:false so an unauthenticated
// visitor gets a fast, single 401 instead of react-query retrying a doomed request
export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: getMe,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })
}
