#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
    echo "Usage: $0 vYYYY.MM.N" >&2
}

if [[ $# -ne 1 || ! "$1" =~ ^v[0-9]{4}\.[0-9]{2}\.[0-9]+$ ]]; then
    usage
    exit 2
fi

version="$1"
repo="$(git rev-parse --show-toplevel)"
cd "$repo"

if [[ -n "$(git status --porcelain)" ]]; then
    echo 'Refusing to build a release from a dirty working tree.' >&2
    exit 1
fi

git_sha="$(git rev-parse HEAD)"
short_sha="$(git rev-parse --short=12 HEAD)"
build_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
image_tag="${version}-${short_sha}"
release_root="${HAOCHAT_RELEASE_ROOT:-/home/ubuntu/haochat-releases}"
release_dir="$release_root/$image_tag"

export HAOCHAT_RELEASE_VERSION="$version"
export HAOCHAT_IMAGE_TAG="$image_tag"
export HAOCHAT_GIT_SHA="$git_sha"
export HAOCHAT_BUILD_TIME="$build_time"

mkdir -p "$release_dir"
chmod 750 "$release_root" "$release_dir"

sudo -n docker compose --profile ai-memory config --quiet
sudo -n docker compose --profile ai-memory build embedding backend web

umask 077
{
    printf 'HAOCHAT_RELEASE_VERSION=%q\n' "$version"
    printf 'HAOCHAT_IMAGE_TAG=%q\n' "$image_tag"
    printf 'HAOCHAT_GIT_SHA=%q\n' "$git_sha"
    printf 'HAOCHAT_BUILD_TIME=%q\n' "$build_time"
} > "$release_dir/release.env"

sudo -n docker image inspect \
    "haochat-embedding:$image_tag" \
    "haochat-backend:$image_tag" \
    "haochat-web:$image_tag" \
    > "$release_dir/images.json"

{
    printf 'version=%s\n' "$version"
    printf 'image_tag=%s\n' "$image_tag"
    printf 'git_sha=%s\n' "$git_sha"
    printf 'build_time=%s\n' "$build_time"
    printf 'embedding_image=%s\n' "$(sudo -n docker image inspect "haochat-embedding:$image_tag" --format '{{.Id}}')"
    printf 'backend_image=%s\n' "$(sudo -n docker image inspect "haochat-backend:$image_tag" --format '{{.Id}}')"
    printf 'web_image=%s\n' "$(sudo -n docker image inspect "haochat-web:$image_tag" --format '{{.Id}}')"
} > "$release_dir/manifest.txt"

sha256sum "$release_dir/release.env" "$release_dir/images.json" "$release_dir/manifest.txt" \
    > "$release_dir/SHA256SUMS"

printf 'RELEASE_DIR=%s\n' "$release_dir"
printf 'IMAGE_TAG=%s\n' "$image_tag"
cat "$release_dir/manifest.txt"
