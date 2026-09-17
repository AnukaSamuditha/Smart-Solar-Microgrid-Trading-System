# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Critical: this Next.js version is not the one in your training data

This project uses **Next.js 16.3.5**, a version newer than your training data with breaking API/convention changes. Before writing or modifying any Next.js code (routing, layouts, data fetching, config, etc.), read the relevant docs bundled in `node_modules/next/dist/docs/` (resolved relative to this directory — in a monorepo, `next` may not be hoisted to the repo root). Key sections:

- `01-app/01-getting-started/` and `01-app/02-guides/` — App Router conventions
- `01-app/03-api-reference/` — authoritative API reference
- `03-architecture/` — compiler, fast refresh, browser support

Do not assume App Router patterns (route props, config shape, etc.) from prior Next.js knowledge without checking these docs first — e.g. `app/layout.tsx` already uses a generated `LayoutProps<"/">` type rather than a hand-written props interface.

## Commands

```bash
npm run dev     # start dev server (localhost:3000)
npm run build   # production build
npm run start   # run production build
npm run lint    # eslint (flat config via eslint.config.mjs)
```

There is no test runner configured in this package yet.

## Architecture

This is the **frontend** of a multi-project workspace (`smart-solar-microgrid-trading-system`); a separate .NET backend (`smart-solar-mgt-api`) lives as a sibling directory outside this package and is not part of this Next.js project.

The app itself is a `create-next-app` scaffold (App Router, TypeScript, Tailwind CSS v4 via `@tailwindcss/postcss`) with shadcn/ui layered on top:

- `app/layout.tsx` — root layout, loads Geist fonts via `next/font/google`, wraps `children` in `QueryClientProviderCom`
- `providers/QueryClientProviderCom.tsx` — client component wrapping TanStack Query's `QueryClientProvider`; the `QueryClient` instance is created with `useState` (per-mount, not module-level) so it isn't shared across requests/users under SSR
- `providers/api-client.ts` — the shared axios instance (`withCredentials: true`) all API functions call through. Attaches the `X-XSRF-TOKEN` header (double-submit CSRF) on mutating requests, and on a 401 attempts one silent `/auth/refresh` before retrying the original request.
- `app/page.tsx` — home page (still the default create-next-app boilerplate content)
- `app/(auth)/login`, `app/(auth)/reset-password` — sign-in and invitation-acceptance pages, backed by `components/login-form.tsx` / `components/reset-password-form.tsx`
- `app/globals.css` — Tailwind entry point + shadcn theme tokens (CSS variables, `oklch` palette, light/dark via `.dark` class)
- `components/ui/` — shadcn-generated components (currently `button.tsx`, `card.tsx`, `field.tsx`, `input.tsx`, `label.tsx`, `separator.tsx`)
- `lib/api/` — one file per backend resource (`auth.ts`, `users.ts`), each a thin wrapper around `apiClient`; `lib/api/errors.ts` normalizes axios errors into `ApiError` with user-facing messages
- `lib/validations/` — zod schemas paired with `react-hook-form` (`login.ts`, `reset-password.ts`)
- `hooks/` — TanStack Query hooks wrapping `lib/api/` functions (`use-login`, `use-logout`, `use-me`, `use-accept-invitation`); components never call `lib/api/` directly
- `lib/utils.ts` — re-exports `cn` from the `cn` package
- `components.json` — shadcn config: `base-nova` style, `base` component library (`@base-ui/react`, not Radix), `neutral` base color, `lucide-react` icons
- Path alias `@/*` maps to the project root (`tsconfig.json`); shadcn aliases (`@/components`, `@/lib`, `@/hooks`) build on this

Add more shadcn components with `npx shadcn@latest add <component>`.

## Backend integration

The backend (`smart-solar-mgt-api`) is called **directly from the browser** — there is no
BFF/proxy layer in this Next.js app. `NEXT_PUBLIC_API_URL` (see `.env.example`) points at it and
must be listed in that API's `Cors:AllowedOrigins`. Auth is HttpOnly-cookie based
(`access_token`, `refresh_token`, set by the API — see its `CLAUDE.md` and
`docs/authentication-implementation-approach.md`), so the tokens are never readable from this
app's JS; session state is bootstrapped client-side via `useMe()` (`GET /api/v1/users/me`), not
via Next.js middleware (middleware can't see cookies set on the API's own origin). No dashboard
or protected-route shell exists yet beyond the login/reset-password pages and the `useLogout`
hook — that's expected, not a gap in this integration.
