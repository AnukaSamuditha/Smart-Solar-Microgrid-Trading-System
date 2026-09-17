// deterministic per-user avatar background — same seed always resolves to the same gradient,
// so a user's color doesn't shift between renders/reloads
const GRADIENTS = [
  "bg-gradient-to-br from-indigo-500 to-violet-600",
  "bg-gradient-to-br from-amber-500 to-orange-600",
  "bg-gradient-to-br from-rose-500 to-pink-600",
  "bg-gradient-to-br from-cyan-500 to-sky-600",
  "bg-gradient-to-br from-emerald-500 to-teal-600",
  "bg-gradient-to-br from-lime-500 to-green-600",
]

export function getAvatarGradient(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0
  }
  const index = Math.abs(hash) % GRADIENTS.length
  return GRADIENTS[index]
}
