import { computed, toValue, type Ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useCachedStore } from '@/stores/cached'

/**
 * 统一获取用户信息 hook
 * @param uid 用户 ID
 * @description 引入该Hook后，可响应式获取用户信息
 */
export const useUserInfo = (uid?: number | ComputedRef<number | undefined> | Ref<number>) => {
  const cachedStore = useCachedStore()
  const userInfo = computed(() => (uid && cachedStore.userCachedList[toValue(uid as number)]) || {})
  // 缓存是否已存在不代表数据新鲜（比如曾经以空头像写入过），新鲜度判断统一交给 getBatchUserInfo
  // 内部的 lastModifyTime 10 分钟过期逻辑，这里不再用"是否已有缓存"来短路请求，否则一旦写入过
  // 一条陈旧/空白数据就永远不会再刷新。
  const resultUid = toValue(uid as number)
  if (resultUid) {
    cachedStore.getBatchUserInfo([resultUid])
  }
  return userInfo
}

/**
 * 统一获取用户徽章信息 hook
 * @param itemId 用户徽章ID
 * @description 引入该Hook后，可响应式获取用户徽章信息
 */
export const useBadgeInfo = (itemId?: number | ComputedRef<number | undefined>) => {
  const cachedStore = useCachedStore()
  const badgeInfo = computed(
    () => (itemId && cachedStore.badgeCachedList[toValue(itemId as number)]) || {},
  )
  // 新鲜度判断统一交给 getBatchBadgeInfo 内部的 lastModifyTime 过期逻辑，理由同 useUserInfo
  const resultItemId = toValue(itemId as number)
  if (resultItemId) {
    cachedStore.getBatchBadgeInfo([resultItemId])
  }
  return badgeInfo
}
