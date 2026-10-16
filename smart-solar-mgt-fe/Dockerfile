# ---- Base -------------------------------------------------------------------
FROM node:24-alpine AS base
# libc6-compat: some prebuilt native binaries (e.g. SWC) expect glibc symbols on Alpine.
RUN apk add --no-cache libc6-compat
WORKDIR /app

# ---- Dependencies -----------------------------------------------------------
FROM base AS deps
COPY package.json package-lock.json ./
RUN npm ci

# ---- Build ------------------------------------------------------------------
FROM base AS builder
# The /api/v1/* rewrite target in next.config.ts is baked into the build output, so the API
# URL must be supplied here (docker build --build-arg API_URL=...), not at runtime.
ARG API_URL=http://localhost:5011
ENV API_URL=$API_URL
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---- Runtime ----------------------------------------------------------------
FROM node:24-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S -g 1001 nodejs && adduser -S -u 1001 -G nodejs nextjs

# Standalone output contains server.js and only the traced node_modules; public/ and
# .next/static are not included by default and must be copied alongside it.
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "server.js"]
