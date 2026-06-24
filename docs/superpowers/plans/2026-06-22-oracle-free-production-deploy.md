# HaoChat Oracle Free Production Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a maintainable Oracle Cloud Always Free deployment package for HaoChat.

**Architecture:** Docker Compose runs Caddy, Vue static assets, Spring Boot, MySQL, Redis, MinIO, a one-shot MinIO bucket initializer, and RocketMQ on one VM. Caddy exposes the public site, upload endpoint, and MinIO console with automatic HTTPS.

**Tech Stack:** Docker Compose, Caddy 2, Java 17, Spring Boot 2.6.7, Vue 3, Vite 4, MySQL 8, Redis 7, MinIO, RocketMQ 4.9.x.

---

## File Structure

- `.dockerignore`: keep build contexts small and prevent local artifacts from entering images.
- `.env.example`: document all runtime settings and secrets.
- `docker-compose.yml`: define the production container stack.
- `deploy/caddy/Caddyfile`: serve the frontend and reverse proxy API, WebSocket, MinIO API, and MinIO console.
- `deploy/docker/backend.Dockerfile`: build the Spring Boot jar with Maven and run it with Java 17.
- `deploy/docker/web.Dockerfile`: build the Vue app and package it with Caddy.
- `deploy/rocketmq/broker.conf`: single-broker RocketMQ config for a small VM.
- `deploy/scripts/backup.sh`: create MySQL and volume backups.
- `deploy/scripts/restore.sh`: restore from a selected backup with explicit confirmation.
- `deploy/scripts/validate-deploy-assets.ps1`: local static verification for this workspace.
- `docs/deployment/oracle-free-vm.md`: operator runbook for first deploy, upgrades, backups, and troubleshooting.
- `haochat-server/haochat-chat-server/src/main/resources/application.yml`: set the default active profile.
- `haochat-server/haochat-chat-server/src/main/resources/application-prod.properties`: bind production settings from environment variables.
- `haochat-web/.env.production`: keep browser API and WebSocket traffic on the public Caddy origin.

### Task 1: Production Runtime Configuration

**Files:**
- Create: `haochat-server/haochat-chat-server/src/main/resources/application.yml`
- Create: `haochat-server/haochat-chat-server/src/main/resources/application-prod.properties`
- Modify: `haochat-web/.env.production`

- [x] **Step 1: Add Spring profile selection**

Create `application.yml` with:

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:prod}
```

- [x] **Step 2: Add environment-driven production properties**

Create `application-prod.properties` with database, Redis, MinIO, RocketMQ, WeChat, and AI settings sourced from environment variables.

- [x] **Step 3: Keep frontend production requests same-origin**

Set `VITE_API_PREFIX=` and `VITE_WS_URL=` so Caddy can route `/capi/*` and `/websocket`.

### Task 2: Docker Build and Runtime Stack

**Files:**
- Create: `.dockerignore`
- Create: `.env.example`
- Create: `docker-compose.yml`
- Create: `deploy/docker/backend.Dockerfile`
- Create: `deploy/docker/web.Dockerfile`
- Create: `deploy/caddy/Caddyfile`
- Create: `deploy/rocketmq/broker.conf`

- [x] **Step 1: Add backend Dockerfile**

Use Maven with Eclipse Temurin 17 to build `haochat-chat-server`, then run the jar on Eclipse Temurin 17 JRE.

- [x] **Step 2: Add frontend Dockerfile**

Use Node 20 with Corepack and pnpm to build Vite, then copy `dist` into Caddy.

- [x] **Step 3: Add Compose stack**

Define Caddy, backend, MySQL, Redis, MinIO, MinIO bucket initialization, RocketMQ NameServer, and RocketMQ broker with persistent volumes and restart policies.

- [x] **Step 4: Add Caddy reverse proxy routes**

Route `/capi/*` to Spring Boot, `/websocket*` to Netty, `SITE_DOMAIN` to the frontend, `MINIO_DOMAIN` to MinIO API, and `MINIO_CONSOLE_DOMAIN` to MinIO console.

### Task 3: Operations Runbook and Scripts

**Files:**
- Create: `docs/deployment/oracle-free-vm.md`
- Create: `deploy/scripts/backup.sh`
- Create: `deploy/scripts/restore.sh`

- [x] **Step 1: Document first deploy**

Include Oracle VM prerequisites, DuckDNS setup, `.env` setup, Docker installation pointer, Compose startup, and smoke checks.

- [x] **Step 2: Document upgrades**

Use `docker compose build`, `docker compose up -d`, and backup-before-upgrade as the release path.

- [x] **Step 3: Add backup and restore scripts**

Back up MySQL with `mysqldump` and archive named Docker volumes for MinIO and RocketMQ data.

### Task 4: Verification

**Files:**
- Create: `deploy/scripts/validate-deploy-assets.ps1`

- [x] **Step 1: Add static deployment validation**

Check required files, required config strings, and environment template keys.

- [x] **Step 2: Run validation locally**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scripts\validate-deploy-assets.ps1
```

Expected output:

```text
Deployment asset validation passed.
```

## Self-Review

- Spec coverage: The plan covers Oracle VM architecture, DuckDNS hostnames, Caddy HTTPS, Dockerized services, feature preservation, runtime secrets, backup/restore, and validation.
- Placeholder scan: No task contains unresolved placeholder instructions. Runtime placeholder values are intentional examples in `.env.example`.
- Type consistency: File names, service names, environment keys, and Caddy routes match across the plan and implementation files.
