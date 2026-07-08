// 混合负载基准：多用户发消息（写扩散路径） + 并发拉历史（roomCache 读路径）
// 运行（挂载 perf-local 以读取 tokens）:
//   docker run --rm -i -v //e/opt/haochat/perf-local:/perf grafana/k6 run \
//     -e BASE=http://192.168.1.113 -e ROOM_ID=<roomId> - < perf-local/mixed.js
import http from 'k6/http'
import { check, sleep } from 'k6'
import exec from 'k6/execution'

const tokens = JSON.parse(open('/perf/tokens.json'))
const BASE = __ENV.BASE || 'http://192.168.1.113'
const ROOM_ID = Number(__ENV.ROOM_ID)
const BASE_UID = 90000

export const options = {
  scenarios: {
    senders: {
      executor: 'ramping-vus',
      exec: 'sender',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '30s', target: 0 },
      ],
    },
    readers: {
      executor: 'constant-vus',
      exec: 'reader',
      vus: 30,
      duration: '3m30s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:senders}': ['p(95)<1500'],
    'http_req_duration{scenario:readers}': ['p(95)<800'],
    'http_req_failed{scenario:senders}': ['rate<0.05'],
    'http_req_failed{scenario:readers}': ['rate<0.02'],
  },
}

export function sender() {
  // 每个 VU 绑定一个独立用户，避开 per-uid 频控（5s/3条 + 30s/5条 → 安全间隔 ~6.5s）
  const uid = BASE_UID + ((exec.vu.idInTest - 1) % 200) + 1
  const res = http.post(
    `${BASE}/capi/chat/msg`,
    JSON.stringify({
      roomId: ROOM_ID,
      msgType: 1,
      body: { content: `perf-${Date.now()}-vu${exec.vu.idInTest}`, atUidList: [] },
    }),
    { headers: { Authorization: `Bearer ${tokens[uid]}`, 'Content-Type': 'application/json' } },
  )
  check(res, { 'send ok': r => r.status === 200 && String(r.body).includes('"success":true') })
  sleep(6.5)
}

export function reader() {
  const uid = BASE_UID + ((exec.vu.idInTest - 1) % 200) + 1
  const res = http.get(`${BASE}/capi/chat/public/msg/page?roomId=${ROOM_ID}&pageSize=20`, {
    headers: { Authorization: `Bearer ${tokens[uid]}` },
  })
  check(res, { 'read ok': r => r.status === 200 && String(r.body).includes('"success":true') })
  sleep(0.3)
}
