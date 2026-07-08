import dayjs from 'dayjs'
import type { OpUnitType, ConfigType } from 'dayjs'
import type { MessageType } from '@/services/types'

// 5 分钟 5 * 60 * 1000;
const intervalTime = 300000
// 计数上限 20 条，到达 20 重置
const computedCountMax = 20
// 计数
let computedCount = 0

// 周一 ~ 周日（date.day(): 0=周日 ... 6=周六）
const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 统一的聊天时间格式化（始终按浏览器本地时区显示）。
 * 入参可为时间戳(number) 或 ISO 8601 字符串，dayjs 会自动转为本地时区。
 *  - 今天：14:30
 *  - 昨天：昨天 14:30
 *  - 本周：周一 14:30
 *  - 更早：2026/06/20 14:30
 */
export const formatChatTime = (time: number | string | null | undefined): string => {
  if (time === null || time === undefined || time === '') return ''
  const date = dayjs(time)
  if (!date.isValid()) return ''
  const now = dayjs()
  if (now.isSame(date, 'day')) return date.format('HH:mm')
  if (now.subtract(1, 'day').isSame(date, 'day')) return `昨天 ${date.format('HH:mm')}`
  // 本周：不早于本地周起点（zh-cn + weekday 插件，周一为起点）
  if (!date.isBefore(now.startOf('week'))) return `${weekMap[date.day()]} ${date.format('HH:mm')}`
  return date.format('YYYY/MM/DD HH:mm')
}

// 时间块分隔（保留旧函数名，复用统一格式化）
const timeToStr = (time: number) => formatChatTime(time)

// 超过5分钟，或者超过20条消息，就添加展示时间
const checkTimeInterval = (cur: MessageType, pre: MessageType) => {
  // 如果有一个超过 5 分钟了或者计数达到 20 条了
  if (
    (pre && cur.message.sendTime - pre.message.sendTime > intervalTime) ||
    computedCount >= computedCountMax
  ) {
    // 重置计数
    computedCount = 0
    // 返回时间标记
    return { ...cur, timeBlock: timeToStr(cur.message.sendTime) }
  } else {
    // 时间间隔很短的就累计计数
    computedCount += 1
    return cur
  }
}

export const computedTimeBlock = (list: MessageType[], needFirst = true) => {
  if (!list || list.length === 0) return []
  // 是否需要保留 传入 list 第一个，如果是接口拉回来的消息列表就要保留，如果接收到新消息，需要拿当前消息列表最后一个拿来做时间间隔计算的话，就不需要保留第一个
  const temp = needFirst ? [list[0]] : []
  // 跳过第一个
  for (let index = 1, len = list.length; index < len; index++) {
    const item = list[index]
    // 上个聊天记录
    const preItem = list[index - 1]
    // 超过20分钟，或者超过50条评论，展示时间
    temp.push(checkTimeInterval(item, preItem))
  }
  return temp
}

/**
 * 消息时间戳格式化（统一走 formatChatTime，符合本地时区 + 规范格式）
 * @param timestamp 时间戳或 ISO 8601 字符串
 */
export const formatTimestamp = (timestamp: number | string): string => formatChatTime(timestamp)

/**
 * 消息间隔判断
 * @param {ConfigType} time 输入时间
 * @param {OpUnitType} unit 间隔单位
 * @param {number} diff 间隔值
 * @returns boolean 输入时间是否间隔 now 间隔值以上。
 */
export const isDiffNow = ({
  time,
  unit,
  diff,
}: {
  unit: OpUnitType
  time: ConfigType
  diff: number
}): boolean => {
  return dayjs().diff(dayjs(time), unit) > diff
}

/**
 * 距离现在 10 分钟了
 * @param {ConfigType} time 输入时间
 * @param {OpUnitType} unit 间隔单位
 * @param {number} diff 间隔值
 * @returns boolean 输入时间是否间隔 now 间隔值以上。
 */
export const isDiffNow10Min = (time: ConfigType): boolean => {
  return isDiffNow({ time, unit: 'minute', diff: 10 })
}
