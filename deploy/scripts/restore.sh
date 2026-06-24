#!/usr/bin/env bash
set -euo pipefail

if [ "${CONFIRM_RESTORE:-}" != "yes" ]; then
  echo "Set CONFIRM_RESTORE=yes to restore data. This overwrites current runtime data." >&2
  exit 1
fi

if [ $# -ne 1 ]; then
  echo "Usage: CONFIRM_RESTORE=yes deploy/scripts/restore.sh backups/YYYYMMDD-HHMMSS" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="$1"
cd "$ROOT_DIR"

if [ ! -f "$BACKUP_DIR/mysql.sql" ]; then
  echo "Missing $BACKUP_DIR/mysql.sql" >&2
  exit 1
fi

set -a
source .env
set +a

docker compose down

for volume in minio-data redis-data rocketmq-store rocketmq-logs caddy-data caddy-config; do
  if [ -f "$BACKUP_DIR/${volume}.tgz" ]; then
    docker volume create "haochat_${volume}" >/dev/null
    docker run --rm \
      -v "haochat_${volume}:/volume" \
      -v "$BACKUP_DIR:/backup:ro" \
      alpine:3.20 \
      sh -c "rm -rf /volume/* && tar xzf /backup/${volume}.tgz -C /volume"
  fi
done

docker compose up -d mysql
until docker compose exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent; do
  sleep 2
done
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < "$BACKUP_DIR/mysql.sql"
docker compose up -d

echo "Restore completed from $BACKUP_DIR"
