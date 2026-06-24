import { ref } from 'vue'
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

  function getUserDetailAction() {
    apis
      .getUserDetail()
      .send()
      .then((data) => {
        userInfo.value = { ...userInfo.value, ...data }
      })
      .catch(() => {
        localStorage.removeItem('TOKEN')
        localStorage.removeItem('USER_INFO')
        isSign.value = false
      })
  }

  return { userInfo, isSign, getUserDetailAction }
})
