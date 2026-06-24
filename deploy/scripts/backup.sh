#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "Missing .env. Copy .env.example to .env first." >&2
  exit 1
fi

set -a
source .env
set +a

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$ROOT_DIR/backups/$STAMP"
mkdir -p "$BACKUP_DIR"

docker compose exec -T mysql sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  > "$BACKUP_DIR/mysql.sql"

for volume in minio-data redis-data rocketmq-store rocketmq-logs caddy-data caddy-config; do
  docker run --rm \
    -v "haochat_${volume}:/volume:ro" \
    -v "$BACKUP_DIR:/backup" \
    alpine:3.20 \
    tar czf "/backup/${volume}.tgz" -C /volume .
done

echo "Backup written to $BACKUP_DIR"
