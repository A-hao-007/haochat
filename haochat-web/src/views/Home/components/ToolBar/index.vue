<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useGroupStore } from '@/stores/group'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import { judgeClient } from '@/utils/detectDevice'
import { useTheme } from '@/hooks/useTheme'
import { ElMessageBox } from 'element-plus'
import apis from '@/services/apis'
import { RoomTypeEnum } from '@/enums'

const client = judgeClient()
const visible = ref(false)
const router = useRouter()
const userStore = useUserStore()
const groupStore = useGroupStore()
const globalStore = useGlobalStore()
const chatStore = useChatStore()
const { currentTheme, toggleTheme } = useTheme()

const avatar = computed(() => userStore?.userInfo.avatar)
const unReadMark = computed(() => globalStore.unReadMark)
const showSettingBox = () => (visible.value = true)
const toggleGroupListShow = () => (groupStore.showGroupList = !groupStore.showGroupList)
const isPc = computed(() => client === 'PC')

// 打开AI助手聊天
const AI_USER_ID = 10001
const openAIChat = async () => {
  if (!userStore.isSign) return
  try {
    const result = await apis.sessionDetailWithFriends({ uid: AI_USER_ID }).send()
    globalStore.currentSession.roomId = result.roomId
    globalStore.currentSession.type = RoomTypeEnum.Single
    chatStore.updateSessionLastActiveTime(result.roomId, result)
    router.push('/')
  } catch {
    // 如果还没有好友关系，先跳转到首页让用户通过 @ 方式使用AI
    router.push('/')
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning' })
    userStore.isSign = false
    userStore.userInfo = {}
    localStorage.removeItem('TOKEN')
    localStorage.removeItem('USER_INFO')
    router.push('/login')
  } catch {}
}
</script>

<template>
  <aside class="side-toolbar">
    <Avatar :src="userStore.isSign ? avatar : ''" :size="isPc ? 50 : 40" v-login="showSettingBox" />
    <div class="tool-icons">
      <router-link exactActiveClass="tool-icon-active" to="/">
        <el-badge :value="unReadMark.newMsgUnreadCount" :hidden="unReadMark.newMsgUnreadCount === 0" :max="99">
          <Icon class="tool-icon" icon="chat" :size="28" />
        </el-badge>
      </router-link>
      <router-link v-login-show exactActiveClass="tool-icon-active" to="/contact">
        <el-badge :value="unReadMark.newFriendUnreadCount" :hidden="unReadMark.newFriendUnreadCount === 0" :max="99">
          <Icon class="tool-icon" icon="group" :size="28" />
        </el-badge>
      </router-link>
    </div>

    <div class="menu">
      <!-- AI助手 -->
      <div class="menu-item" title="AI助手" @click="openAIChat">
        <Icon icon="huojian" :size="28" colorful />
        <span class="menu-item-name">AI助手</span>
      </div>
    </div>

    <div class="tool-actions">
      <el-tooltip effect="dark" :content="currentTheme === 'dark' ? '明亮模式' : '暗黑模式'" :placement="isPc ? 'right' : 'bottom'">
        <div class="theme-toggle" @click="toggleTheme">
          <IEpSunny v-if="currentTheme === 'dark'" :size="20" />
          <IEpMoon v-else :size="20" />
        </div>
      </el-tooltip>
      <el-tooltip effect="dark" content="退出登录" :placement="isPc ? 'right' : 'bottom'">
        <div class="logout-btn" @click="handleLogout">
          <IEpSwitchButton :size="18" />
        </div>
      </el-tooltip>
    </div>

    <UserSettingBox v-model="visible" />
  </aside>
</template>

<style lang="scss" src="./styles.scss" scoped />
