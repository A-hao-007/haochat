#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <exact-image-tag>" >&2
    exit 2
fi

image_tag="$1"
repo="$(git rev-parse --show-toplevel)"
release_root="${HAOCHAT_RELEASE_ROOT:-/home/ubuntu/haochat-releases}"
release_dir="$release_root/$image_tag"
release_env="$release_dir/release.env"
cd "$repo"

if [[ ! -f "$release_env" ]]; then
    echo "Release metadata not found: $release_env" >&2
    exit 1
fi

source "$release_env"
if [[ "$HAOCHAT_IMAGE_TAG" != "$image_tag" ]]; then
    echo 'Release metadata does not match the requested image tag.' >&2
    exit 1
fi

for image in haochat-embedding haochat-backend haochat-web; do
    sudo -n docker image inspect "$image:$image_tag" >/dev/null
done

previous_dir=''
if [[ -L "$release_root/current" ]]; then
    previous_dir="$(readlink -f "$release_root/current")"
fi

rollback() {
    local exit_code=$?
    if [[ -n "$previous_dir" && -f "$previous_dir/release.env" ]]; then
        echo 'Deployment failed; restoring previous image tag.' >&2
        source "$previous_dir/release.env"
        sudo -n docker compose --profile ai-memory up -d --no-build --no-deps embedding backend web || true
    fi
    exit "$exit_code"
}
trap rollback ERR

stamp="$(date +%Y%m%d-%H%M%S)"
sudo -n docker inspect haochat-embedding haochat-backend haochat-web \
    > "$release_dir/predeploy-containers-$stamp.json"
sudo -n docker exec haochat-mysql sh -c \
    'exec mysqldump --single-transaction --quick --routines --events --all-databases -uroot -p"$MYSQL_ROOT_PASSWORD"' |
    gzip -1 > "$release_dir/mysql-predeploy-$stamp.sql.gz"
gzip -t "$release_dir/mysql-predeploy-$stamp.sql.gz"

sudo -n docker compose --profile ai-memory up -d --no-build --no-deps embedding backend web

for attempt in $(seq 1 40); do
    embedding_state="$(sudo -n docker inspect haochat-embedding --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}')"
    backend_state="$(sudo -n docker inspect haochat-backend --format '{{.State.Status}}')"
    web_state="$(sudo -n docker inspect haochat-web --format '{{.State.Status}}')"
    if [[ "$embedding_state" == 'healthy' && "$backend_state" == 'running' && "$web_state" == 'running' ]]; then
        break
    fi
    if [[ "$attempt" == 40 ]]; then
        echo "Services did not become ready: embedding=$embedding_state backend=$backend_state web=$web_state" >&2
        exit 1
    fi
    sleep 3
done

curl --silent --show-error --max-time 10 http://127.0.0.1:8080/ >/dev/null
curl --insecure --fail --location --silent --show-error --max-time 15 http://127.0.0.1/ >/dev/null

if [[ -n "$previous_dir" ]]; then
    ln -sfn "$previous_dir" "$release_root/previous"
fi
ln -sfn "$release_dir" "$release_root/current"
trap - ERR

printf 'DEPLOYED=%s\n' "$image_tag"
sudo -n docker ps --filter name=haochat- --format '{{.Names}}|{{.Image}}|{{.Status}}'
