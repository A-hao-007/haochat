// 本地基准：消息历史拉取压测（读路径 + 权限校验缓存路径）
import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<800'],
  },
}

const BASE = __ENV.BASE || 'http://host.docker.internal'
const TOKEN = __ENV.TOKEN
const ROOM_ID = Number(__ENV.ROOM_ID)

const headers = { Authorization: `Bearer ${TOKEN}` }

export default function () {
  const res = http.get(`${BASE}/capi/chat/msg/page?roomId=${ROOM_ID}&pageSize=20`, { headers })
  check(res, {
    'read ok': r => r.status === 200 && String(r.body).includes('"success":true'),
  })
  sleep(0.5)
}
