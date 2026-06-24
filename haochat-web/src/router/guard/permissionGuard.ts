import type { Router } from 'vue-router'

import { useUserStore } from '@/stores/user'

// 白名单，未登录用户可以访问
const whiteList: Array<string | RegExp> = ['/login']

const whiteListTest = (path: string) => {
  return whiteList.some((o) => {
    if (o instanceof RegExp) {
      return o.test(path)
    } else {
      return o === path
    }
  })
}

const createPermissionGuard = (router: Router) => {
  router.beforeEach(async (to, _from, next) => {
    // 是否登录
    const userStore = useUserStore()
    const isSign = userStore.isSign

    // 已登录用户访问登录页 -> 跳转到聊天页
    if (to.path === '/login' && isSign) {
      return next({ path: '/', replace: true })
    }

    // 白名单页面或已登录 -> 放行
    if (whiteListTest(to.path) || isSign) {
      return next()
    }

    // 未登录 -> 跳转到登录页
    return next({ path: '/login', replace: true })
  })
}

export default createPermissionGuard
