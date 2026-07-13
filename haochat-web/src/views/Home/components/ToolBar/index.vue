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
    await apis.logout(localStorage.getItem('REFRESH_TOKEN') || undefined).send().catch(() => undefined)
    userStore.isSign = false
    userStore.userInfo = {}
    localStorage.removeItem('TOKEN')
    localStorage.removeItem('REFRESH_TOKEN')
    localStorage.removeItem('USER_INFO')
    // 整页刷新回登录页：SPA 内部跳转不会清掉内存里的消息/会话/当前房间等状态，
    // 下一个账号在同一浏览器登录时会看到上一个账号的聊天内容（已实际发生过）
    window.location.replace('/login')
  } catch {}
}
</script>

<template>
  <aside class="side-toolbar">
    <div class="toolbar-brand">
      <span class="brand-mark">H</span>
      <span class="brand-name">HaoChat</span>
    </div>

    <div class="tool-icons">
      <router-link class="tool-link" exactActiveClass="tool-icon-active" to="/">
        <el-badge :value="unReadMark.newMsgUnreadCount" :hidden="unReadMark.newMsgUnreadCount === 0" :max="99">
          <Icon class="tool-icon" icon="chat" :size="20" />
        </el-badge>
        <span>消息</span>
      </router-link>
      <router-link v-login-show class="tool-link" exactActiveClass="tool-icon-active" to="/contact">
        <el-badge :value="unReadMark.newFriendUnreadCount" :hidden="unReadMark.newFriendUnreadCount === 0" :max="99">
          <Icon class="tool-icon" icon="group" :size="20" />
        </el-badge>
        <span>联系人</span>
      </router-link>
      <router-link v-login-show class="tool-link" exactActiveClass="tool-icon-active" to="/groups">
        <!-- 包一层隐藏的 el-badge，与消息/联系人保持相同 DOM 结构，否则图标与文字会错位换行 -->
        <el-badge hidden>
          <Icon class="tool-icon" icon="group" :size="20" />
        </el-badge>
        <span>群组</span>
      </router-link>
    </div>

    <div class="menu">
      <div class="menu-item" title="AI助手" @click="openAIChat">
        <Icon icon="huojian" :size="18" colorful />
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
      <button class="profile-entry" type="button" v-login="showSettingBox">
        <Avatar :src="userStore.isSign ? avatar : ''" :size="30" />
        <span>我的主页</span>
      </button>
      <button class="logout-btn" type="button" @click="handleLogout">退出</button>
    </div>

    <UserSettingBox v-model="visible" />
  </aside>
</template>

<style lang="scss" src="./styles.scss" scoped />
