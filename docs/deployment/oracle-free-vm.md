# Oracle Free VM Deployment Runbook

This runbook deploys HaoChat as a public site on one Oracle Cloud Always Free VM.

## 1. Accounts and DNS

Create these yourself because they require personal ownership:

1. Oracle Cloud account.
2. One Always Free VM, preferably Ampere A1 with at least 2 OCPU and 12 GB RAM. Use Ubuntu 22.04 or 24.04.
3. Three DuckDNS hostnames pointing to the VM public IP:
   - `SITE_DOMAIN`, for example `haochat-demo.duckdns.org`
   - `MINIO_DOMAIN`, for example `haochat-demo-s3.duckdns.org`
   - `MINIO_CONSOLE_DOMAIN`, for example `haochat-demo-minio.duckdns.org`

Open ports 22, 80, and 443 in the Oracle security list and the VM firewall.

## 2. Install Docker on the VM

Install Docker Engine and the Compose plugin from the Docker documentation for Ubuntu. After installation, verify:

```bash
docker --version
docker compose version
```

## 3. Prepare Runtime Configuration

Copy the repository to the VM, then create `.env`:

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Change every password and set the public addresses.

For a temporary IP-only Alibaba/Tencent deployment, use values like:

```dotenv
SITE_DOMAIN=http://47.96.43.107
SITE_ORIGIN=http://47.96.43.107
MINIO_ENDPOINT=http://47.96.43.107:9000
MINIO_CONSOLE_URL=http://47.96.43.107:9001
MINIO_DOMAIN=http://127.0.0.1:19000
MINIO_CONSOLE_DOMAIN=http://127.0.0.1:19001
```

For a domain deployment, use HTTPS hostnames instead. Keep `DEEPSEEK_ENABLED=false` until you have a real API key.

## 4. First Start

Build and start the stack:

```bash
docker compose --env-file .env build
docker compose --env-file .env up -d
```

Watch startup logs:

```bash
docker compose logs -f backend
docker compose logs -f web
```

Open:

```text
https://SITE_DOMAIN
https://MINIO_CONSOLE_DOMAIN
```

The app should load from HTTPS. Register a user, log in, open another browser, register a second user, and test direct chat.

The `minio-init` container creates the upload bucket and CORS rule automatically. If uploads fail, check it with:

```bash
docker compose logs minio-init
```

## 5. Backups

Run before every upgrade:

```bash
bash deploy/scripts/backup.sh
```

Backups are written under `backups/YYYYMMDD-HHMMSS`.

## 6. Restore

Restore only when you intentionally want to overwrite current runtime data:

```bash
CONFIRM_RESTORE=yes bash deploy/scripts/restore.sh backups/YYYYMMDD-HHMMSS
```

## 7. Upgrade

Before upgrading:

```bash
bash deploy/scripts/backup.sh
```

Then update code and rebuild:

```bash
docker compose --env-file .env build
docker compose --env-file .env up -d
docker compose ps
```

## 8. Operations

Show service status:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f rocketmq-broker
```

Restart one service:

```bash
docker compose restart backend
```

Stop all services:

```bash
docker compose down
```

Do not use `docker compose down -v` unless you have a verified backup. The `-v` flag deletes persistent data volumes.

## 9. Small VM Fallback

If the VM has less than 4 GB RAM, RocketMQ may be too heavy. The maintainable fallback is to replace MQ-backed async paths with synchronous local execution in code, then remove `rocketmq-namesrv` and `rocketmq-broker` from Compose. Do this only after verifying which message paths are used by login, push, and chat delivery.
