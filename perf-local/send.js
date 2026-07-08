// 本地基准：消息发送压测（写扩散路径）
// 运行: docker run --rm -i --network host -e TOKEN -e ROOM_ID grafana/k6 run - < perf-local/send.js
// 或安装 k6 后: k6 run -e TOKEN=... -e ROOM_ID=... perf-local/send.js
import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m', target: 20 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1500'],
  },
}

const BASE = __ENV.BASE || 'http://host.docker.internal'
const TOKEN = __ENV.TOKEN
const ROOM_ID = Number(__ENV.ROOM_ID)

const headers = {
  Authorization: `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
}

export default function () {
  const payload = JSON.stringify({
    roomId: ROOM_ID,
    msgType: 1,
    body: {
      content: `perf-${Date.now()}-vu${__VU}-it${__ITER}`,
      atUidList: [],
    },
  })
  const res = http.post(`${BASE}/capi/chat/msg`, payload, { headers })
  check(res, {
    'send ok': r => r.status === 200 && String(r.body).includes('"success":true'),
  })
  sleep(1)
}
