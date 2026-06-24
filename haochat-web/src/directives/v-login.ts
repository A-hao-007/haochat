import type { Directive } from 'vue'
import { useRouter } from 'vue-router'

import { useUserStore } from '@/stores/user'

const handler = (fn: Function) => {
  const userStore = useUserStore()
  // 没登录先跳转到登录页
  if (!userStore.isSign) {
    const router = useRouter()
    router.push('/login')
    return
  }
  fn?.()
}

const vLogin: Directive = {
  mounted(el, binding) {
    if (typeof binding.value !== 'function') return
    el.fn = handler.bind(el, binding.value)
    el.addEventListener('click', el.fn)
  },
  unmounted(el, binding) {
    if (typeof binding.value !== 'function') return
    el.removeEventListener('click', el.fn)
  },
}

export default vLogin
