# Smart Solar Microgrid Trading System — Web Frontend

Next.js 16 (App Router) + TanStack Query + shadcn/ui. Calls the `smart-solar-mgt-api` backend directly (no BFF layer). See `CLAUDE.md` for architecture notes.

## Prerequisites

- Node.js 20+
- `smart-solar-mgt-api` running locally (see its own `README.md`)

## 1. Install dependencies

```bash
npm install
```

## 2. Configure the API URL

```bash
cp .env.example .env.local
```

`NEXT_PUBLIC_API_URL` must match the running backend, and that origin must be listed in the backend's `Cors:AllowedOrigins`.

## 3. Run the dev server

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Other commands

```bash
npm run build   # production build
npm run start   # run production build
npm run lint    # eslint
```

No test runner is configured yet.

## Project layout

| Path | Purpose |
|---|---|
| `app/` | routes (App Router); `(auth)` group holds login/reset-password |
| `providers/api-client.ts` | shared axios instance — CSRF header, silent-refresh-on-401 |
| `providers/QueryClientProviderCom.tsx` | TanStack Query provider |
| `lib/api/` | one file per backend resource (`auth.ts`, `users.ts`), wraps `apiClient` |
| `lib/api/errors.ts` | normalizes axios errors into `ApiError` with user-facing messages |
| `lib/validations/` | zod schemas for `react-hook-form` |
| `hooks/` | TanStack Query hooks (`use-login`, `use-logout`, `use-me`, `use-accept-invitation`) |
| `components/ui/` | shadcn-generated primitives |

Components call hooks, hooks call `lib/api/`, `lib/api/` calls `apiClient`. Never skip a layer.

## Auth model

- Backend sets `HttpOnly` cookies (`access_token`, `refresh_token`) on login/refresh — never readable from this app's JS.
- `apiClient` sends cookies automatically (`withCredentials: true`) and attaches the `X-XSRF-TOKEN` header on mutating requests from the readable `XSRF-TOKEN` cookie.
- On a 401, `apiClient` attempts one silent `POST /auth/refresh` and retries the original request.
- Session state is bootstrapped client-side via `useMe()` (`GET /users/me`) — **not** Next.js middleware, since middleware can't see cookies set on the API's own origin.
- No protected-route shell/dashboard exists yet beyond the login and reset-password pages — expected, not a gap.

## Troubleshooting

- **CORS error in browser console**: backend's `Cors:AllowedOrigins` doesn't include this app's origin.
- **Login succeeds but `/me` fails**: check the backend is actually setting cookies (inspect `Set-Cookie` in Network tab) and `NEXT_PUBLIC_API_URL` is correct.
- **403 on submit**: `XSRF-TOKEN` cookie missing or stale — log in again.
- **Form won't submit / fields look empty on submit**: usually a broken `ref` on a form input — shadcn inputs must be `forwardRef`-wrapped for `react-hook-form`.
