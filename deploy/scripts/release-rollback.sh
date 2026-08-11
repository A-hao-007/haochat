#!/usr/bin/env bash
set -Eeuo pipefail

repo="$(git rev-parse --show-toplevel)"
release_root="${HAOCHAT_RELEASE_ROOT:-/home/ubuntu/haochat-releases}"
target="${1:-}"
cd "$repo"

if [[ -z "$target" ]]; then
    if [[ ! -L "$release_root/previous" ]]; then
        echo 'No previous release is recorded.' >&2
        exit 1
    fi
    release_dir="$(readlink -f "$release_root/previous")"
else
    release_dir="$release_root/$target"
fi

if [[ ! -f "$release_dir/release.env" ]]; then
    echo "Release metadata not found: $release_dir/release.env" >&2
    exit 1
fi

source "$release_dir/release.env"
sudo -n docker compose --profile ai-memory up -d --no-build --no-deps embedding backend web
ln -sfn "$release_dir" "$release_root/current"

printf 'ROLLED_BACK_TO=%s\n' "$HAOCHAT_IMAGE_TAG"
echo 'Database restoration is intentionally not automatic.'
sudo -n docker ps --filter name=haochat- --format '{{.Names}}|{{.Image}}|{{.Status}}'
