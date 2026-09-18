"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { cn } from "cn"
import { CheckCircle2Icon, EyeIcon, EyeOffIcon, SmartphoneIcon } from "lucide-react"
import { Controller, useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useAcceptInvitation } from "@/hooks/use-accept-invitation"
import {
  resetPasswordSchema,
  type ResetPasswordFormValues,
} from "@/lib/validations/reset-password"

export function ResetPasswordForm({
  token,
  className,
  ...props
}: React.ComponentProps<"form"> & { token: string }) {
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const acceptInvitation = useAcceptInvitation()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  })

  const onSubmit = handleSubmit((values) => {
    acceptInvitation.mutate({ token, newPassword: values.password })
  })

  if (acceptInvitation.isSuccess) {
    // Prosumers have no web dashboard login (Backoffice/Grid Operator only, per
    // project-specification.md section 3) — point them at the mobile app instead of a
    // "sign in" link that would only reject them with ProsumerMobileOnly
    if (acceptInvitation.data.accountType === "Prosumer") {
      return (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-border bg-muted/30 px-6 py-8 text-center">
          <SmartphoneIcon className="size-8 text-primary" />
          <div className="flex flex-col gap-1">
            <p className="text-sm font-medium">Thank you — your account is ready</p>
            <p className="text-sm text-muted-foreground">
              Sign in with your NIC and new password using the Wattex mobile app to manage your
              energy reservations.
            </p>
          </div>
        </div>
      )
    }

    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-border bg-muted/30 px-6 py-8 text-center">
        <CheckCircle2Icon className="size-8 text-primary" />
        <div className="flex flex-col gap-1">
          <p className="text-sm font-medium">Password updated</p>
          <p className="text-sm text-muted-foreground">
            You can now sign in with your new password.
          </p>
        </div>
        <Button render={<a href="/login" />} className="mt-2 h-9 w-full">
          Back to sign in
        </Button>
      </div>
    )
  }

  return (
    <form
      className={cn("flex flex-col gap-6", className)}
      autoComplete="off"
      onSubmit={onSubmit}
      {...props}
    >
      <FieldGroup>
        <Field data-invalid={!!errors.password}>
          <FieldLabel htmlFor="password">New password</FieldLabel>
          <div className="relative">
            <Controller
              name="password"
              control={control}
              render={({ field }) => (
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  className="h-10 pr-9"
                  aria-invalid={!!errors.password}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  ref={field.ref}
                />
              )}
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-muted-foreground hover:text-foreground"
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              {showPassword ? (
                <EyeOffIcon className="size-4" />
              ) : (
                <EyeIcon className="size-4" />
              )}
            </button>
          </div>
          <FieldError errors={[errors.password]} />
        </Field>

        <Field data-invalid={!!errors.confirmPassword}>
          <FieldLabel htmlFor="confirmPassword">Confirm password</FieldLabel>
          <div className="relative">
            <Controller
              name="confirmPassword"
              control={control}
              render={({ field }) => (
                <Input
                  id="confirmPassword"
                  type={showConfirmPassword ? "text" : "password"}
                  autoComplete="new-password"
                  className="h-10 pr-9"
                  aria-invalid={!!errors.confirmPassword}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  ref={field.ref}
                />
              )}
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword((v) => !v)}
              className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-muted-foreground hover:text-foreground"
              aria-label={
                showConfirmPassword ? "Hide password" : "Show password"
              }
            >
              {showConfirmPassword ? (
                <EyeOffIcon className="size-4" />
              ) : (
                <EyeIcon className="size-4" />
              )}
            </button>
          </div>
          <FieldError errors={[errors.confirmPassword]} />
        </Field>

        <Field>
          <Button
            type="submit"
            disabled={!token || acceptInvitation.isPending}
            className="h-10 border-0 bg-[#d9ff43] text-[#10130d] shadow-[0_6px_12px_-4px_rgba(217,255,67,0.45)] transition-shadow hover:bg-[#c2e63a] hover:shadow-[0_8px_16px_-4px_rgba(217,255,67,0.6)]"
          >
            {acceptInvitation.isPending ? "Resetting..." : "Reset password"}
          </Button>
        </Field>

        {!token ? (
          <FieldDescription className="text-center text-destructive">
            This reset link is invalid or missing a token. Request a new one
            from the sign-in page.
          </FieldDescription>
        ) : acceptInvitation.isError ? (
          <FieldDescription className="text-center text-destructive">
            {acceptInvitation.error.message}
          </FieldDescription>
        ) : (
          <FieldDescription className="text-center">
            Use at least 8 characters, including a number and an uppercase
            letter.
          </FieldDescription>
        )}
      </FieldGroup>
    </form>
  )
}
