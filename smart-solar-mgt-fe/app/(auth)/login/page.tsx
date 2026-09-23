import { ZapIcon } from "lucide-react"

import { AlreadyAuthenticatedRedirect } from "@/components/auth/already-authenticated-redirect"
import { LoginForm } from "@/components/login-form"

export default function LoginPage() {
  return (
    <div className="flex flex-col gap-8">
      <AlreadyAuthenticatedRedirect />

      <div className="flex items-center gap-2.5 lg:hidden">
        <div className="auth-logo-badge flex size-8 items-center justify-center rounded-lg">
          <ZapIcon className="size-4.5 text-[#10130d]" strokeWidth={2.5} />
        </div>
        <span className="text-[15px] font-medium tracking-tight">SolarSync</span>
      </div>

      <div className="flex flex-col gap-1.5">
        <h2 className="text-2xl font-medium tracking-tight">Sign in</h2>
        <p className="text-sm text-muted-foreground">
          Enter your credentials to access your energy dashboard.
        </p>
      </div>

      <LoginForm />
    </div>
  )
}
