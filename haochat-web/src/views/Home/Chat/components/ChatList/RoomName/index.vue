<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { MessageType } from '@/services/types'
import { MsgEnum } from '@/enums'
import SettingBox from '@/views/Home/Chat/components/ChatList/RoomName/components/SettingBox/SettingBox.vue'

const chatStore = useChatStore()
const isShowSetting = ref<boolean>(false)
const showSearch = ref(false)
const searchKeyword = ref('')
const searchResults = ref<MessageType[]>([])

/** 搜索消息 */
const doSearch = () => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    searchResults.value = []
    return
  }
  const messages = chatStore.chatMessageList || []
  searchResults.value = messages.filter((msg) => {
    if (msg.message.type === MsgEnum.TEXT) {
      const content = msg.message.body?.content || ''
      return content.toLowerCase().includes(keyword)
    }
    return false
  })
}

/** 点击搜索结果跳转到消息位置 */
const scrollToMessage = (msgId: number) => {
  const index = chatStore.getMsgIndex(msgId)
  if (index >= 0) {
    // 发射事件让 ChatList 滚动到指定消息
    const chatList = document.querySelector('.chat-msg-list')
    const msgElements = chatList?.querySelectorAll('.chat-item')
    if (msgElements && msgElements[index]) {
      msgElements[index].scrollIntoView({ behavior: 'smooth', block: 'center' })
      // 高亮消息
      ;(msgElements[index] as HTMLElement).style.backgroundColor = 'var(--bg-active)'
      setTimeout(() => {
        ;(msgElements[index] as HTMLElement).style.backgroundColor = ''
      }, 2000)
    }
    showSearch.value = false
    searchKeyword.value = ''
    searchResults.value = []
  }
}

const getMsgPreview = (msg: MessageType) => {
  if (msg.message.type === MsgEnum.TEXT) {
    return (msg.message.body?.content || '').substring(0, 60)
  }
  return '[非文本消息]'
}

const clearSearch = () => {
  showSearch.value = false
  searchKeyword.value = ''
  searchResults.value = []
}
</script>

<template>
  <div class="room-name">
    <span class="name">{{ chatStore.currentSessionInfo?.name }}</span>
    <div class="room-actions">
      <el-tooltip content="搜索消息" effect="dark">
        <span class="action-btn" @click="showSearch = !showSearch">
          <IEpSearch :size="16" />
        </span>
      </el-tooltip>
      <span
        v-if="chatStore.isGroup && !chatStore.currentSessionInfo?.hot_Flag"
        class="action-btn"
        @click="isShowSetting = true"
      >
        设置
      </span>
    </div>
  </div>

  <!-- 搜索面板 -->
  <div v-if="showSearch" class="search-panel">
    <div class="search-input-wrapper">
      <IEpSearch class="search-icon" :size="14" />
      <input
        v-model="searchKeyword"
        class="search-input"
        placeholder="搜索聊天记录..."
        @input="doSearch"
        @keyup.escape="clearSearch"
      />
      <span v-if="searchKeyword" class="search-close" @click="clearSearch">
        <IEpClose :size="14" />
      </span>
    </div>
    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <div
        v-for="msg in searchResults.slice(0, 20)"
        :key="msg.message.id"
        class="search-result-item"
        @click="scrollToMessage(msg.message.id)"
      >
        <span class="result-user">{{ msg.fromUser.username }}:</span>
        <span class="result-text">{{ getMsgPreview(msg) }}</span>
      </div>
    </div>
    <div v-else-if="searchKeyword && searchResults.length === 0" class="search-empty">
      未找到相关消息
    </div>
  </div>

  <el-drawer v-model="isShowSetting" direction="rtl" append-to-body>
    <template #header>
      <h4 class="text-[#f5f5f5]">设置</h4>
    </template>
    <template #default>
      <SettingBox />
    </template>
  </el-drawer>
</template>

<style lang="scss" src="./styles.scss">
.el-drawer__header {
  margin-bottom: 0 !important;
}
</style>
