<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import { RoomTypeEnum } from '@/enums'

import UserList from '../UserList/index.vue'
import ChatList from '../ChatList/index.vue'
import SendBar from './SendBar/index.vue'

const isSelect = ref(false)
const globalStore = useGlobalStore()
const chatStore = useChatStore()
const currentSession = computed(() => globalStore.currentSession)
const showUserList = computed(
  () => !!chatStore.currentSessionInfo && currentSession.value.type === RoomTypeEnum.Group,
)

// 输入状态（模拟 - 将来可从 WebSocket 获取）
const typingUsers = reactive<Map<number, string>>(new Map())

const updateTyping = (uid: number, name: string) => {
  typingUsers.set(uid, name)
  // 2秒后自动清除
  setTimeout(() => {
    typingUsers.delete(uid)
  }, 3000)
}

defineExpose({ updateTyping })
</script>

<template>
  <div class="chat-box">
    <div class="chat-wrapper">
      <template v-if="isSelect">
        <ElIcon :size="160" color="var(--font-light)"><IEpChatDotRound /></ElIcon>
      </template>
      <div v-else class="chat">
        <ChatList />
        <!-- 输入状态提示 -->
        <div v-if="typingUsers.size > 0" class="typing-indicator">
          <span class="typing-dots">
            <span class="dot" />
            <span class="dot" />
            <span class="dot" />
          </span>
          <span class="typing-text"> {{ [...typingUsers.values()].join(', ') }} 正在输入... </span>
        </div>
        <SendBar :onTyping="(uid: number, name: string) => updateTyping(uid, name)" />
      </div>
    </div>
    <UserList v-show="showUserList" />
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
