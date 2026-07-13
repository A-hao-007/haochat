<script setup lang="ts">
import { computed, nextTick, onMounted, provide, ref, watch } from 'vue'
import throttle from 'lodash/throttle'
import { useChatStore } from '@/stores/chat'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import type { MessageType } from '@/services/types'
import { MsgEnum } from '@/enums'
import { formatTimestamp } from '@/utils/computedTime'
import VirtualList from '@/components/VirtualList'
import MsgItem from './MsgItem/index.vue'
import RoomName from './RoomName/index.vue'
import GroupAnnouncement from './GroupAnnouncement.vue'

const chatStore = useChatStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const virtualListRef = ref()
const messageOptions = computed(() => chatStore.currentMessageOptions)
const chatMessageList = computed(() => chatStore.chatMessageList)
const currentNewMsgCount = computed(() => chatStore.currentNewMsgCount)
// 新用户没有任何会话时，currentRoomId 其实是 globalStore 里的占位默认值（roomId:1），并非真的对应一个会话，
// 这种情况下永远不会触发 getMsgList，loading 态也永远不会被清除，所以要用"是否存在真实会话"来判断，而不是 currentRoomId 是否为空
const hasCurrentRoom = computed(() => chatStore.sessionList.length > 0)

// 回到底部
const goToBottom = () => {
  if (virtualListRef.value) {
    virtualListRef.value.scrollToBottom()
    chatStore.clearNewMsgCount()
  }
}

// 回到最新消息
const goToNewMessage = () => {
  // 未读消息数 = 总数 - 新消息数
  virtualListRef.value.scrollToIndex(
    chatMessageList.value.length - (currentNewMsgCount.value?.count || 0),
  )
  chatStore.clearNewMsgCount()
}

// —— 未读分割线跳转 ——
// 点过一次或滚到分割线附近后就不再显示；切换会话时重置
const unreadJumpDismissed = ref(false)
watch(
  () => globalStore.currentSession?.roomId,
  () => (unreadJumpDismissed.value = false),
)
const firstUnreadIndex = computed(() => {
  const roomId = globalStore.currentSession?.roomId
  const targetId = roomId === undefined ? undefined : chatStore.firstUnreadMsgId.get(roomId)
  if (targetId === undefined) return -1
  return chatMessageList.value.findIndex((m) => m.message.id === targetId)
})
const showUnreadJump = computed(() => !unreadJumpDismissed.value && firstUnreadIndex.value >= 0)
const goToFirstUnread = () => {
  if (firstUnreadIndex.value >= 0) {
    virtualListRef.value?.scrollToIndex(firstUnreadIndex.value)
  }
  unreadJumpDismissed.value = true
}

// —— 会话内消息搜索（搜索已加载的文本消息） ——
const searchVisible = ref(false)
const searchKeyword = ref('')
watch(
  () => globalStore.currentSession?.roomId,
  () => {
    searchVisible.value = false
    searchKeyword.value = ''
  },
)
const searchResults = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return []
  return chatMessageList.value
    .filter(
      (m) =>
        m.message.type === MsgEnum.TEXT &&
        typeof m.message.body?.content === 'string' &&
        m.message.body.content.toLowerCase().includes(kw),
    )
    .slice(-50) // 最多展示50条，避免大房间里渲染过多结果
    .reverse() // 新的在前
})
const senderName = (m: MessageType) =>
  cachedStore.userCachedList[m.fromUser.uid]?.name || `用户${m.fromUser.uid}`

const jumpToMsg = (msgId: number) => {
  const idx = chatMessageList.value.findIndex((m) => m.message.id === msgId)
  if (idx < 0) return
  searchVisible.value = false
  virtualListRef.value?.scrollToIndex(idx)
  // 等虚拟列表渲染出目标项后加个短暂高亮，帮用户定位
  nextTick(() => {
    setTimeout(() => {
      const el = document
        .querySelector(`[data-message-id="${msgId}"]`)
        ?.closest('.chat-item') as HTMLElement | null
      if (el) {
        el.classList.add('msg-flash')
        setTimeout(() => el.classList.remove('msg-flash'), 1600)
      }
    }, 150)
  })
}

// 提供虚拟列表 ref 给子组件使用
provide('virtualListRef', virtualListRef)

onMounted(() => {
  nextTick(() => {
    chatStore.chatListToBottomAction = () => {
      goToBottom()
    }
  })
})

// 到顶部时触发函数 记录旧的滚动高度，加载更多消息后滚动回加载时那条消息的位置
const onToTop = async () => {
  if (messageOptions.value?.isLoading) return
  const oldIndex = virtualListRef.value.getSizes()
  await chatStore.loadMore()
  virtualListRef.value.scrollToIndex(virtualListRef.value.getSizes() - oldIndex)
}

// 滚动时触发函数，主要处理新消息提示
const onScroll = throttle((eventData) => {
  const { offset, clientSize, scrollSize } = eventData

  if (!offset || !clientSize || !scrollSize) return

  // 是否已滚动到底部最后一个可视范围内
  const isScrollEnd = offset + clientSize >= scrollSize - clientSize
  if (isScrollEnd) {
    currentNewMsgCount.value && (currentNewMsgCount.value.isStart = false)
    chatStore.clearNewMsgCount()
  } else {
    currentNewMsgCount.value && (currentNewMsgCount.value.isStart = true)
  }
}, 100)

const getKey = (item: MessageType) => item.message.id
</script>

<template>
  <div class="chat-msg-list">
    <RoomName />
    <!-- 会话内消息搜索 -->
    <el-popover
      v-if="hasCurrentRoom"
      placement="bottom-end"
      :width="340"
      trigger="click"
      :teleported="false"
      v-model:visible="searchVisible"
      popper-class="msg-search-popper"
    >
      <template #reference>
        <span class="msg-search-btn" title="搜索聊天记录">
          <el-icon :size="16"><IEpSearch /></el-icon>
        </span>
      </template>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索当前会话已加载的消息"
        size="small"
        clearable
      />
      <div class="msg-search-results scroll-hover">
        <div
          v-for="r in searchResults"
          :key="r.message.id"
          class="msg-search-result"
          @click="jumpToMsg(r.message.id)"
        >
          <div class="msg-search-result-top">
            <span class="name">{{ senderName(r) }}</span>
            <span class="time">{{ formatTimestamp(r.message.sendTime) }}</span>
          </div>
          <div class="snippet">{{ r.message.body.content }}</div>
        </div>
        <div v-if="searchKeyword.trim() && searchResults.length === 0" class="msg-search-empty">
          没有找到相关消息
        </div>
      </div>
    </el-popover>
    <GroupAnnouncement v-if="chatStore.isGroup" />
    <el-icon v-if="hasCurrentRoom && messageOptions?.isLoading" :size="14" class="loading">
      <IEpLoading />
      消息加载中
    </el-icon>
    <VirtualList
      v-if="chatMessageList?.length"
      ref="virtualListRef"
      class="virtual-list scroll-hover"
      dataPropName="msg"
      :data="chatMessageList"
      :data-key="getKey"
      :item="MsgItem"
      :size="20"
      @totop="onToTop"
      @scroll="onScroll"
      @ok="goToBottom"
    />
    <!-- <VideoPlayer></VideoPlayer> -->
    <template
      v-if="(!hasCurrentRoom || !messageOptions?.isLoading) && chatMessageList?.length === 0"
    >
      <div class="empty">暂无消息，快来发送第一条消息吧~</div>
    </template>
    <span
      class="new-msgs-tips"
      v-show="currentNewMsgCount?.count && currentNewMsgCount.count > 0"
      @click="goToNewMessage"
    >
      {{ currentNewMsgCount?.count }} 条新消息
      <el-icon :size="10"><IEpArrowDownBold /></el-icon>
    </span>
    <!-- 回到未读：进入会话时有未读消息则可一键跳到未读分割线 -->
    <span class="unread-jump-tips" v-show="showUnreadJump" @click="goToFirstUnread">
      <el-icon :size="10"><IEpArrowUpBold /></el-icon>
      回到未读
    </span>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
