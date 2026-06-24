FROM node:20-alpine AS build

WORKDIR /workspace
RUN corepack enable
COPY haochat-web/package.json haochat-web/pnpm-lock.yaml haochat-web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY haochat-web/ ./
ARG VITE_API_PREFIX=
ARG VITE_WS_URL=
ENV VITE_API_PREFIX=${VITE_API_PREFIX}
ENV VITE_WS_URL=${VITE_WS_URL}
RUN pnpm run build-only

FROM caddy:2-alpine

COPY --from=build /workspace/dist /srv
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
EXPOSE 80 443
