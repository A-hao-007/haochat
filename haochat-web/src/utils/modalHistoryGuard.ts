/**
 * 弹窗返回键守卫：弹窗打开时往 history 压入一条记录，
 * 浏览器“后退”触发时优先关闭最上层弹窗，而不是退出到登录态之前的页面/系统。
 * 原生 History API 实现，不引入额外依赖。
 */
type CloseFn = () => void

const stack: CloseFn[] = []
let listening = false
let programmaticBackCount = 0

function ensureListener() {
  if (listening) return
  listening = true
  window.addEventListener('popstate', () => {
    if (programmaticBackCount > 0) {
      programmaticBackCount--
      return
    }
    const close = stack.pop()
    if (close) close()
  })
}

/** 弹窗打开时调用：压入一条历史记录，记录关闭回调 */
export function pushModalGuard(onClose: CloseFn) {
  ensureListener()
  history.pushState({ __modalGuard: true }, '', location.href)
  stack.push(onClose)
}

/** 弹窗通过非后退方式关闭（点击关闭按钮/遮罩/ESC）时调用：清理已压入的历史记录 */
export function popModalGuard() {
  if (stack.length === 0) return
  stack.pop()
  programmaticBackCount++
  history.back()
}
