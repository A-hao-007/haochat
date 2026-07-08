#!/usr/bin/env bash
# 本地压测种子脚本：创建测试用户 + 自签 token + 建压测群
# 用法: bash perf-local/seed.sh  （在 E:\opt\haochat 下运行，栈需已启动）
set -euo pipefail
cd "$(dirname "$0")/.."

# 从 .env 读取密码
MYSQL_PASSWORD=$(grep -E '^MYSQL_PASSWORD=' .env | cut -d= -f2-)
REDIS_PASSWORD=$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)
JWT_SECRET=$(grep -E '^JWT_SECRET=' .env | cut -d= -f2-)

BASE_UID=90000
USER_COUNT=21   # 1 个发送者 + 20 个接收者

echo "==> 1/4 插入 ${USER_COUNT} 个测试用户 (uid ${BASE_UID}+1..${BASE_UID}+${USER_COUNT})"
SQL="INSERT IGNORE INTO user (id, name, avatar, sex, open_id, status) VALUES "
for i in $(seq 1 $USER_COUNT); do
  uid=$((BASE_UID + i))
  SQL+="($uid, 'perf_$uid', '', 1, 'perf-openid-$uid', 0)"
  [ "$i" -lt "$USER_COUNT" ] && SQL+=","
done
SQL+=";"
docker exec haochat-mysql mysql -uhaochat -p"$MYSQL_PASSWORD" haochat -e "$SQL"

echo "==> 2/4 自签 JWT 并写入 Redis（与 LoginService 相同的 HS256 + haochat:userToken:uid_N 约定）"
node - "$JWT_SECRET" "$BASE_UID" "$USER_COUNT" <<'EOF' > perf-local/tokens.json
const crypto = require('crypto');
const [secret, baseUid, count] = process.argv.slice(2); // argv: [node, '-', ...args]
const b64u = (s) => Buffer.from(s).toString('base64url');
const tokens = {};
for (let i = 1; i <= Number(count); i++) {
  const uid = Number(baseUid) + i;
  const header = b64u(JSON.stringify({ typ: 'JWT', alg: 'HS256' }));
  const payload = b64u(JSON.stringify({ uid, createTime: Math.floor(Date.now() / 1000) }));
  const sig = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  tokens[uid] = `${header}.${payload}.${sig}`;
}
console.log(JSON.stringify(tokens, null, 2));
EOF
# 注意：应用的 RedisUtils 用 Jackson 序列化存储，字符串在 Redis 里带 JSON 双引号
node -e "
const t = require('./perf-local/tokens.json');
for (const [uid, token] of Object.entries(t)) {
  console.log('SET haochat:userToken:uid_' + uid + ' \"\\\\\"' + token + '\\\\\"\" EX 432000');
}
" | docker exec -i haochat-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning | sort | uniq -c
echo "tokens 已写入 perf-local/tokens.json 与 Redis"

echo "==> 3/4 用发送者身份建压测群（拉入其余 20 人）"
# Caddy 站点地址绑定 SITE_ORIGIN（Host 必须匹配），不能用 localhost
SITE_ORIGIN=$(grep -E '^SITE_ORIGIN=' .env | cut -d= -f2-)
SENDER_UID=$((BASE_UID + 1))
SENDER_TOKEN=$(node -e "console.log(require('./perf-local/tokens.json')['$SENDER_UID'])")
UID_LIST=$(seq -s, $((BASE_UID + 2)) $((BASE_UID + USER_COUNT)))
RESP=$(curl -s -X POST "$SITE_ORIGIN/capi/room/group" \
  -H "Authorization: Bearer $SENDER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"uidList\":[$UID_LIST],\"name\":\"perf-group\"}")
echo "$RESP"
ROOM_ID=$(node -e "const r=$RESP; console.log(r.data?.id ?? '')" 2>/dev/null || true)

echo "==> 4/4 写出压测环境变量"
cat > perf-local/env.sh <<EOV
export PERF_BASE=$SITE_ORIGIN
export PERF_TOKEN=$SENDER_TOKEN
export PERF_ROOM_ID=$ROOM_ID
export PERF_HOT_ROOM_ID=1
EOV
echo "完成。压测群 roomId=$ROOM_ID；source perf-local/env.sh 后运行 k6"
