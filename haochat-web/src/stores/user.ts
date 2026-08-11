import { ref, watch } from 'vue'
import apis from '@/services/apis'
import { defineStore } from 'pinia'
import type { UserInfoType } from '@/services/types'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<Partial<UserInfoType>>({})
  // 从 localStorage 恢复登录态
  const token = localStorage.getItem('TOKEN')
  const isSign = ref(!!token)

  let localUserInfo = {}
  try {
    localUserInfo = JSON.parse(localStorage.getItem('USER_INFO') || '{}')
  } catch {
    localUserInfo = {}
  }

  if (!Object.keys(userInfo.value).length && Object.keys(localUserInfo).length) {
    userInfo.value = localUserInfo
  }

  // Keep the lightweight bootstrap profile in sync after name/avatar/email changes.
  watch(
    userInfo,
    (value) => {
      if (isSign.value && value.uid) {
        localStorage.setItem('USER_INFO', JSON.stringify(value))
      }
    },
    { deep: true },
  )

  function getUserDetailAction() {
    apis
      .getUserDetail()
      .send()
      .then((data) => {
        userInfo.value = { ...userInfo.value, ...data }
      })
      .catch(() => {
        localStorage.removeItem('TOKEN')
        localStorage.removeItem('REFRESH_TOKEN')
        localStorage.removeItem('USER_INFO')
        isSign.value = false
      })
  }

  return { userInfo, isSign, getUserDetailAction }
})
