"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { cn } from "cn"
import { EyeIcon, EyeOffIcon } from "lucide-react"
import { useRouter } from "next/navigation"
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
import { useLogin } from "@/hooks/use-login"
import { loginSchema, type LoginFormValues } from "@/lib/validations/login"

export function LoginForm({
  className,
  ...props
}: React.ComponentProps<"form">) {
  const [showPassword, setShowPassword] = useState(false)
  const router = useRouter()
  const login = useLogin()

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  const onSubmit = handleSubmit((values) => {
    login.mutate(values, {
      onSuccess: () => router.push("/"),
    })
  })

  return (
    <form
      className={cn("flex flex-col gap-6", className)}
      autoComplete="off"
      onSubmit={onSubmit}
      {...props}
    >
      <FieldGroup>
        <Field data-invalid={!!errors.email}>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Controller
            name="email"
            control={control}
            render={({ field }) => (
              <Input
                id="email"
                type="email"
                placeholder="you@company.com"
                autoComplete="off"
                data-1p-ignore
                data-lpignore="true"
                readOnly
                onFocus={(e) => e.currentTarget.removeAttribute("readonly")}
                className="h-10"
                aria-invalid={!!errors.email}
                name="wattex-email"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                ref={field.ref}
              />
            )}
          />
          <FieldError errors={[errors.email]} />
        </Field>
        <Field data-invalid={!!errors.password}>
          <div className="flex items-center">
            <FieldLabel htmlFor="password">Password</FieldLabel>
            <a
              href="/reset-password"
              className="ml-auto text-[13px] text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
            >
              Forgot password?
            </a>
          </div>
          <div className="relative">
            <Controller
              name="password"
              control={control}
              render={({ field }) => (
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="off"
                  data-1p-ignore
                  data-lpignore="true"
                  readOnly
                  onFocus={(e) => e.currentTarget.removeAttribute("readonly")}
                  className="h-10 pr-9"
                  aria-invalid={!!errors.password}
                  name="wattex-password"
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
        <Field>
          <Button
            type="submit"
            disabled={login.isPending}
            className="h-10 border-0 bg-[#d9ff43] text-[#10130d] shadow-[0_6px_12px_-4px_rgba(217,255,67,0.45)] transition-shadow hover:bg-[#c2e63a] hover:shadow-[0_8px_16px_-4px_rgba(217,255,67,0.6)]"
          >
            {login.isPending ? "Signing in..." : "Sign in"}
          </Button>
        </Field>
        {login.isError ? (
          <FieldDescription className="text-center text-destructive">
            {login.error.message}
          </FieldDescription>
        ) : (
          <FieldDescription className="text-center">
            Access is provisioned by your administrator.
          </FieldDescription>
        )}
      </FieldGroup>
    </form>
  )
}
