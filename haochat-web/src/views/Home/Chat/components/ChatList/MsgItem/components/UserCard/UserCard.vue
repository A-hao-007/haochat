<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { CacheUserItem } from '@/services/types'
import apis from '@/services/apis'
import { OnlineEnum, RoomTypeEnum } from '@/enums'
import { useChatStore } from '@/stores/chat'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import { useUserStore } from '@/stores/user'
import { useGroupStore } from '@/stores/group'

const chatStore = useChatStore()
const globalStore = useGlobalStore()
const cacheStore = useCachedStore()
const userStore = useUserStore()
const groupStore = useGroupStore()

const props = defineProps<{
  user: CacheUserItem
}>()

const userCard = ref<HTMLDivElement>()

/** 获取在线状态 */
const onlineStatus = () => {
  const member = groupStore.userList?.find((m) => m.uid === props.user.uid)
  if (!member) return { isOnline: false, lastSeen: '' }
  return {
    isOnline: member.activeStatus === OnlineEnum.ONLINE,
    lastSeen: member.lastOptTime ? new Date(member.lastOptTime).toLocaleString() : '',
  }
}

const sendMsg = async () => {
  const result = await apis.sessionDetailWithFriends({ uid: props.user.uid }).send()
  globalStore.currentSession.roomId = result.roomId
  globalStore.currentSession.type = RoomTypeEnum.Single
  chatStore.updateSessionLastActiveTime(result.roomId, result)
}

onMounted(() => {
  cacheStore.getBatchBadgeInfo(props.user.itemIds || [])
})
</script>

<template>
  <div ref="userCard" class="user-card">
    <!-- 顶部信息 -->
    <div class="user-card_top">
      <div class="user-card_top-avatar">
        <el-avatar shape="square" :size="56" :src="user.avatar" />
        <!-- 在线状态 -->
        <span
          class="online-dot"
          :class="{ online: onlineStatus().isOnline }"
        />
      </div>
      <div class="user-card_top-info">
        <el-tooltip effect="dark" :content="user.name" placement="top-start">
          <div class="user-card_top-info_name">{{ user.name }}</div>
        </el-tooltip>
        <div class="user-card_top-info_id">UID: {{ user.uid }}</div>
        <div class="user-card_top-info_place">
          <IEpLocationInformation :size="12" />
          {{ user.locPlace || '未知' }}
        </div>
      </div>
    </div>

    <!-- 状态信息 -->
    <div class="user-card_status">
      <div class="status-row">
        <span
          class="status-indicator"
          :class="onlineStatus().isOnline ? 'online' : 'offline'"
        />
        <span class="status-text">
          {{ onlineStatus().isOnline ? '在线' : '离线' }}
        </span>
      </div>
    </div>

    <!-- 徽章 -->
    <div v-if="user.itemIds?.length" class="user-card_badge">
      <div class="badge-label">徽章</div>
      <div class="badge-list">
        <div
          v-for="itemId in user.itemIds"
          :key="itemId"
          class="badge-item"
          :class="{ active: user.wearingItemId === itemId }"
        >
          <img
            :src="cacheStore.badgeCachedList[itemId]?.img"
            :alt="cacheStore.badgeCachedList[itemId]?.describe"
            :title="cacheStore.badgeCachedList[itemId]?.describe"
          />
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="user-card_actions">
      <button class="action-btn primary" @click="sendMsg" v-if="userStore.isSign">
        <IEpChatDotRound :size="16" />
        <span>发消息</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss" src="./styles.scss"></style>
