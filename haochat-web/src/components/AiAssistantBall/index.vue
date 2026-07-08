<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import apis from '@/services/apis'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import { RoomTypeEnum } from '@/enums'

// AI 机器人 uid（后端 haochat.deepseek.uid）
const AI_UID = 10001

const router = useRouter()
const userStore = useUserStore()
const globalStore = useGlobalStore()
const chatStore = useChatStore()
const loading = ref(false)

// 点击悬浮球：打开与 AI 的会话
const openAi = async () => {
  if (!userStore.isSign) {
    router.push('/login')
    return
  }
  if (loading.value) return
  loading.value = true
  try {
    const result = await apis.sessionDetailWithFriends({ uid: AI_UID }).send()
    globalStore.currentSession.roomId = result.roomId
    globalStore.currentSession.type = RoomTypeEnum.Single
    chatStore.updateSessionLastActiveTime(result.roomId, result)
    if (router.currentRoute.value.path !== '/') router.push('/')
  } catch (e: any) {
    ElMessage.error(e?.message || '无法打开 AI 助手')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <button class="ai-ball" :class="{ loading }" title="AI 助手" @click="openAi">
    <span class="ai-ball-icon">🤖</span>
  </button>
</template>

<style scoped>
.ai-ball {
  position: fixed;
  right: 28px;
  bottom: 36px;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #2f66e8, #5b8bff);
  border: none;
  border-radius: 50%;
  box-shadow: 0 10px 28px rgba(47, 102, 232, 0.36);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.ai-ball:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 14px 34px rgba(47, 102, 232, 0.45);
}

.ai-ball.loading {
  opacity: 0.7;
  cursor: default;
}

.ai-ball-icon {
  font-size: 26px;
  line-height: 1;
}

@media (max-width: 768px) {
  .ai-ball {
    right: 16px;
    bottom: 84px;
    width: 48px;
    height: 48px;
  }

  .ai-ball-icon {
    font-size: 22px;
  }
}
</style>
