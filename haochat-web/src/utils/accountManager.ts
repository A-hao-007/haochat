/**
 * 最近登录账号管理（用于「切换账号」与「记住密码」）。
 * 最多保留 3 个账号，最近登录的排在最前。
 * token 仅在「记住密码」时保存，用于免密切换；未勾选则不保存 token。
 */

export type RecentAccount = {
  uid: number
  name: string
  avatar?: string
  username?: string
  /** 免密切换用，仅在记住密码时存在 */
  token?: string
  /** 最近登录时间戳 */
  lastLogin: number
}

const KEY = 'RECENT_ACCOUNTS'
const MAX = 3

export const getRecentAccounts = (): RecentAccount[] => {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) || '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

const save = (list: RecentAccount[]) => {
  localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)))
}

/** 登录成功后写入/更新一个账号 */
export const upsertAccount = (acc: RecentAccount, remember: boolean) => {
  const list = getRecentAccounts().filter((a) => a.uid !== acc.uid)
  list.unshift({ ...acc, token: remember ? acc.token : undefined, lastLogin: Date.now() })
  save(list)
}

/** 退出登录时清除某账号的 token（仍保留在列表中以便切换时输入密码） */
export const clearAccountToken = (uid: number) => {
  const list = getRecentAccounts().map((a) => (a.uid === uid ? { ...a, token: undefined } : a))
  save(list)
}

export const removeAccount = (uid: number) => {
  save(getRecentAccounts().filter((a) => a.uid !== uid))
}
