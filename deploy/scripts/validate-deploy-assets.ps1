$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $root

$requiredFiles = @(
  ".env.example",
  "docker-compose.yml",
  "deploy/caddy/Caddyfile",
  "deploy/docker/backend.Dockerfile",
  "deploy/docker/web.Dockerfile",
  "deploy/rocketmq/broker.conf",
  "deploy/scripts/backup.sh",
  "deploy/scripts/restore.sh",
  "docs/deployment/oracle-free-vm.md",
  "haochat-server/haochat-chat-server/src/main/resources/application-prod.properties",
  "haochat-web/.env.production"
)

foreach ($file in $requiredFiles) {
  if (-not (Test-Path -LiteralPath $file)) {
    throw "Missing required file: $file"
  }
}

$compose = Get-Content -LiteralPath "docker-compose.yml" -Raw
foreach ($service in @("web:", "backend:", "mysql:", "redis:", "minio:", "minio-init:", "rocketmq-namesrv:", "rocketmq-broker:")) {
  if ($compose -notmatch [regex]::Escape($service)) {
    throw "docker-compose.yml does not define service marker $service"
  }
}

$prod = Get-Content -LiteralPath "haochat-server/haochat-chat-server/src/main/resources/application-prod.properties" -Raw
foreach ($key in @(
  "spring.datasource.url",
  "spring.redis.host",
  "oss.endpoint",
  "rocketmq.name-server",
  "wx.mp.configs[0].app-id",
  "haochat.deepseek.use",
  "haochat.ai.memory.enabled",
  "haochat.ai.provider.primary.base-url"
)) {
  if ($prod -notmatch [regex]::Escape($key)) {
    throw "application-prod.properties missing $key"
  }
}

$env = Get-Content -LiteralPath ".env.example" -Raw
foreach ($key in @(
  "SITE_DOMAIN",
  "SITE_ORIGIN",
  "MINIO_ENDPOINT",
  "MINIO_DOMAIN",
  "MYSQL_ROOT_PASSWORD",
  "REDIS_PASSWORD",
  "JWT_SECRET",
  "MINIO_ROOT_PASSWORD",
  "AI_MEMORY_ENABLED",
  "NEO4J_PASSWORD"
)) {
  if ($env -notmatch "(?m)^$key=") {
    throw ".env.example missing $key"
  }
}

$caddy = Get-Content -LiteralPath "deploy/caddy/Caddyfile" -Raw
foreach ($route in @("backend:8080", "backend:8090", "minio:9000", "minio:9001")) {
  if ($caddy -notmatch [regex]::Escape($route)) {
    throw "Caddyfile missing reverse proxy target $route"
  }
}

Write-Host "Deployment asset validation passed."
