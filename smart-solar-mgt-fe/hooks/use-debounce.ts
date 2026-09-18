import { useEffect, useState } from "react"

// returns `value`, but only updates after it has stopped changing for `delayMs` — used to
// delay backend search requests until the user pauses typing, instead of one call per keystroke
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timeout = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timeout)
  }, [value, delayMs])

  return debounced
}
