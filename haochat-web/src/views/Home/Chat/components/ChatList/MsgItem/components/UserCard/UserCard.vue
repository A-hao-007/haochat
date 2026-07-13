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
import { useContactStore } from '@/stores/contacts'
import { ElMessage } from 'element-plus'

const chatStore = useChatStore()
const globalStore = useGlobalStore()
const cacheStore = useCachedStore()
const userStore = useUserStore()
const groupStore = useGroupStore()
const contactStore = useContactStore()

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

/** 是否本人 */
const isSelf = () => props.user.uid === userStore.userInfo?.uid

/** 添加好友：已是好友提示，否则打开验证消息弹窗发送申请 */
const addFriend = async () => {
  if (isSelf()) {
    ElMessage.info('这是你自己哦~')
    return
  }
  // 确保好友列表已加载后再判断
  if (!contactStore.contactsList.length) {
    await contactStore.getContactList(true)
  }
  const already = contactStore.contactsList.some((c) => c.uid === props.user.uid)
  if (already) {
    ElMessage.info('已是好友')
    return
  }
  // 复用全局加好友弹窗（输入验证消息 → 发送申请）
  globalStore.addFriendModalInfo.uid = props.user.uid
  globalStore.addFriendModalInfo.show = true
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
        <span class="online-dot" :class="{ online: onlineStatus().isOnline }" />
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
        <span class="status-indicator" :class="onlineStatus().isOnline ? 'online' : 'offline'" />
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
    <div class="user-card_actions" v-if="userStore.isSign && !isSelf()">
      <button class="action-btn primary" @click="sendMsg">
        <IEpChatDotRound :size="16" />
        <span>发消息</span>
      </button>
      <button class="action-btn" @click="addFriend">
        <IEpPlus :size="16" />
        <span>添加好友</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss" src="./styles.scss"></style>
