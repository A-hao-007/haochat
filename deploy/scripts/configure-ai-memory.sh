#!/bin/sh
set -eu

cd /home/ubuntu/haochat

upsert_env() {
  key="$1"
  value="$2"
  tmp="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    $0 ~ "^" key "=" { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' .env > "$tmp"
  chmod --reference=.env "$tmp"
  mv "$tmp" .env
}

neo4j_password="$(sed -n 's/^NEO4J_PASSWORD=//p' .env | tail -n 1 | tr -d '\r')"
if [ -z "$neo4j_password" ] || [ "$neo4j_password" = "change-neo4j-password" ]; then
  neo4j_password="$(openssl rand -hex 24)"
fi

embedding_key="$(sed -n 's/^AI_EMBEDDING_API_KEY=//p' .env | tail -n 1 | tr -d '\r')"
if [ -z "$embedding_key" ] || [ "$embedding_key" = "local-haochat-embedding" ]; then
  embedding_key="$(openssl rand -hex 24)"
fi

upsert_env COMPOSE_PROFILES ai-memory
upsert_env AI_MEMORY_ENABLED true
upsert_env AI_QUERY_REWRITE_ENABLED true
upsert_env AI_EMBEDDING_ENABLED true
upsert_env AI_EMBEDDING_BASE_URL http://embedding:8000/v1
upsert_env AI_EMBEDDING_API_KEY "$embedding_key"
upsert_env AI_EMBEDDING_MODEL BAAI/bge-small-zh-v1.5
upsert_env AI_EMBEDDING_QUERY_PREFIX '""'
upsert_env AI_EMBEDDING_DOCUMENT_PREFIX '""'
upsert_env LOCAL_EMBEDDING_MODEL BAAI/bge-small-zh-v1.5
upsert_env LOCAL_EMBEDDING_THREADS 2
upsert_env AI_MEMORY_SYNC_BATCH_SIZE 20
upsert_env AI_MEMORY_MAX_SYNC_RETRIES 12
upsert_env AI_MEMORY_SYNC_RETRY_DELAY_SECONDS 60
upsert_env QDRANT_URL http://qdrant:6333
upsert_env QDRANT_COLLECTION haochat_memory
upsert_env NEO4J_URL http://neo4j:7474
upsert_env NEO4J_USERNAME neo4j
upsert_env NEO4J_PASSWORD "$neo4j_password"

echo 'AI memory environment configured (secrets retained or generated).'
