# HaoChat Oracle Free Production Deploy Design

## Goal

Deploy HaoChat as a maintainable public communication site on an Oracle Cloud Always Free VM, using free DuckDNS hostnames and automatic HTTPS.

## Architecture

One VM runs the complete application stack with Docker Compose. Caddy terminates HTTPS and serves the Vue build, then proxies HTTP API traffic to Spring Boot and WebSocket traffic to the Netty WebSocket listener. MySQL, Redis, MinIO, and RocketMQ run as private containers on the same Docker network.

## Public Hostnames

- `SITE_DOMAIN`: the public Caddy site address, for example `haochat-demo.duckdns.org` or `http://47.96.43.107`.
- `SITE_ORIGIN`: the browser origin allowed to upload to MinIO, for example `https://haochat-demo.duckdns.org` or `http://47.96.43.107`.
- `MINIO_ENDPOINT`: the public S3-compatible upload endpoint used in presigned URLs.
- `MINIO_CONSOLE_URL`: the optional MinIO admin console URL.
- `MINIO_DOMAIN` and `MINIO_CONSOLE_DOMAIN`: Caddy hostnames used when MinIO is served through Caddy.

In domain mode, all hostnames point to the same VM IP and Caddy obtains certificates. In IP-only mode, the site is served over HTTP and MinIO is exposed on ports 9000 and 9001.

## Services

- `web`: Caddy plus the built Vue app.
- `backend`: Spring Boot API on port 8080 and Netty WebSocket on port 8090.
- `mysql`: MySQL 8.0 with the repository SQL loaded on first boot.
- `redis`: Redis 7 with AOF persistence and password protection.
- `minio`: object storage for uploads.
- `minio-init`: one-shot bucket and CORS initializer for browser uploads.
- `rocketmq-namesrv` and `rocketmq-broker`: retained to preserve existing async login, push, and message consumer behavior.

## Feature Policy

Core chat features remain enabled: registration, login, contacts, friend requests, group chat, direct chat, message history, WebSocket presence, message push, and file uploads.

External-account features remain configurable but default to non-blocking settings:

- DeepSeek is disabled unless `DEEPSEEK_ENABLED=true` and `DEEPSEEK_KEY` is supplied.
- WeChat configuration is present with safe placeholder values so the app can boot without a real WeChat account.
- RocketMQ stays enabled in the default production compose file. If the VM is too small, a later maintenance change can replace MQ paths with synchronous local execution.

## Maintainability

Configuration lives in `.env`, copied from `.env.example`. Runtime secrets are not committed. Dockerfiles are deterministic build entrypoints, while Compose defines all persistent volumes. Backup and restore scripts operate on MySQL dumps and Docker volumes so application releases do not delete data.

## Deployment Constraints

The user must create the Oracle Cloud account, VM, and DuckDNS hostnames because those require personal identity and account ownership. After SSH access is available, this repository can be copied to the VM and started with Docker Compose.

## Verification

Local verification checks that all deployment assets exist and that key configuration values are wired consistently. Full build verification requires a Linux host with Docker because this Windows workspace lacks Git, Maven, pnpm, and Docker.
