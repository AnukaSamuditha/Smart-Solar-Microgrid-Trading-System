import { ZapIcon } from "lucide-react"

import { ResetPasswordForm } from "@/components/reset-password-form"

export default async function ResetPasswordPage(
  props: PageProps<"/reset-password">
) {
  const searchParams = await props.searchParams
  const token = typeof searchParams.token === "string" ? searchParams.token : ""

  return (
    <div className="flex flex-col gap-8">
      <div className="flex items-center gap-2.5 lg:hidden">
        <div className="auth-logo-badge flex size-8 items-center justify-center rounded-lg">
          <ZapIcon className="size-4.5 text-[#10130d]" strokeWidth={2.5} />
        </div>
        <span className="text-[15px] font-medium tracking-tight">SolarSync</span>
      </div>

      <div className="flex flex-col gap-1.5">
        <h2 className="text-2xl font-medium tracking-tight">
          Reset password
        </h2>
        <p className="text-sm text-muted-foreground">
          Choose a new password for your account.
        </p>
      </div>

      <ResetPasswordForm token={token} />
    </div>
  )
}
