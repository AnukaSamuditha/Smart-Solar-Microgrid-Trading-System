import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { getAvatarGradient } from "@/lib/avatar-gradient"
import { cn } from "@/lib/utils"

interface UserAvatarProps {
  seed: string
  label: string
  className?: string
}

// renders the first letter of the username (or email, as a fallback) on a deterministic gradient
export function UserAvatar({ seed, label, className }: UserAvatarProps) {
  const initial = label.trim().charAt(0).toUpperCase() || "?"

  return (
    <Avatar className={cn("after:border-transparent", className)}>
      <AvatarFallback className={cn("font-medium text-white", getAvatarGradient(seed))}>
        {initial}
      </AvatarFallback>
    </Avatar>
  )
}
