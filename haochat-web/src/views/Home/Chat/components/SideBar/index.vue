<script setup lang="ts">
import { computed, onBeforeMount, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useGlobalStore } from '@/stores/global'
import { IsAllUserEnum } from '@/services/types'
import { MsgEnum, RoomTypeEnum } from '@/enums'
import { useUserInfo } from '@/hooks/useCached'
import { formatTimestamp } from '@/utils/computedTime'
import renderReplyContent from '@/utils/renderReplyContent'
import Skeleton from '@/components/Skeleton/index.vue'
import apis from '@/services/apis'
import { ElMessage } from 'element-plus'

const chatStore = useChatStore()
const globalStore = useGlobalStore()
const isLoading = ref(true)
const contextMenu = ref({ show: false, x: 0, y: 0, roomId: 0 })

onBeforeMount(async () => {
  await chatStore.getSessionList()
  isLoading.value = false
})

const currentSession = computed(() => globalStore.currentSession)

const sessionList = computed(() =>
  chatStore.sessionList.map((item) => {
    const lastMsg = Array.from(chatStore.messageMap.get(item.roomId)?.values() || [])?.slice(-1)?.[0]
    let LastUserMsg = ''
    if (lastMsg) {
      const lastMsgUserName = useUserInfo(lastMsg.fromUser.uid)
      LastUserMsg =
        lastMsg.message?.type === MsgEnum.RECALL
          ? `${lastMsgUserName.value.name}:'撤回了一条消息'`
          : renderReplyContent(
              lastMsgUserName.value.name,
              lastMsg.message?.type,
              lastMsg.message?.body?.content || lastMsg.message?.body,
            )
    }
    return {
      ...item,
      tag: item.hot_Flag === IsAllUserEnum.Yes ? '官方' : '',
      lastMsg: LastUserMsg || item.text || '欢迎使用HaoChat',
      lastMsgTime: formatTimestamp(item?.activeTime),
      pinned: item.pinned === 1,
      muted: item.muted === 1,
    }
  }),
)

const onSelectSession = (roomId: number, roomType: RoomTypeEnum) => {
  globalStore.currentSession.roomId = roomId
  globalStore.currentSession.type = roomType
}

const onContextMenu = (e: MouseEvent, roomId: number) => {
  e.preventDefault()
  contextMenu.value = { show: true, x: e.clientX, y: e.clientY, roomId }
}

const closeMenu = () => { contextMenu.value.show = false }

const togglePin = async () => {
  const sid = contextMenu.value.roomId
  const session = chatStore.sessionList.find((s) => s.roomId === sid)
  const pinned = !session?.pinned
  try {
    await apis.pinContact(sid, pinned).send()
    chatStore.updateSession(sid, { pinned: pinned ? 1 : 0 })
    ElMessage.success(pinned ? '已置顶' : '已取消置顶')
  } catch { ElMessage.error('操作失败') }
  closeMenu()
}

const toggleMute = async () => {
  const sid = contextMenu.value.roomId
  const session = chatStore.sessionList.find((s) => s.roomId === sid)
  const muted = !session?.muted
  try {
    await apis.muteContact(sid, muted).send()
    chatStore.updateSession(sid, { muted: muted ? 1 : 0 })
    ElMessage.success(muted ? '已开启免打扰' : '已关闭免打扰')
  } catch { ElMessage.error('操作失败') }
  closeMenu()
}

const deleteSession = async () => {
  const sid = contextMenu.value.roomId
  try {
    await apis.deleteContact(sid).send()
    chatStore.removeContact(sid)
    ElMessage.success('会话已删除')
  } catch { ElMessage.error('操作失败') }
  closeMenu()
}

const load = () => { chatStore.getSessionList() }
</script>

<template>
  <aside class="session-panel">
    <div class="session-header">
      <h2>消息</h2>
      <span>{{ sessionList.length }} 个会话</span>
    </div>

    <ul class="chat-message" v-infinite-scroll="load" :infinite-scroll-immediate="false">
      <template v-if="isLoading">
        <li v-for="i in 8" :key="'skeleton-' + i" class="chat-message-item skeleton-item">
          <div class="item-wrapper">
            <Skeleton variant="circle" :width="38" :height="38" />
            <div class="message-info">
              <Skeleton variant="text" width="120px" />
              <Skeleton variant="text" width="180px" />
            </div>
            <Skeleton variant="text" width="48px" />
          </div>
        </li>
      </template>
      <li
        v-for="(item, index) in sessionList"
        :key="index"
        :data-room-id="item.roomId"
        :class="['chat-message-item', { active: currentSession.roomId === item.roomId, pinned: item.pinned }]"
        @click="onSelectSession(item.roomId, item.type)"
        @contextmenu="onContextMenu($event, item.roomId)"
      >
        <div class="item-wrapper">
          <el-badge
            :value="item.unreadCount"
            :max="999"
            :hidden="item.unreadCount < 1"
            :class="['item', { 'badge-muted': item.muted }]"
          >
            <el-avatar shape="circle" :size="42" :src="item.avatar" />
          </el-badge>
          <div class="message-info">
            <div class="info-top">
              <span class="person">{{ item.name }}</span>
              <span v-if="item.tag" class="tag">{{ item.tag }}</span>
              <span v-if="item.pinned" class="pin-icon">📌</span>
              <span v-if="item.muted" class="mute-icon">🔕</span>
            </div>
            <div class="message-message">{{ item.lastMsg }}</div>
          </div>
          <span class="message-time">{{ item.lastMsgTime }}</span>
        </div>
      </li>
    </ul>
  </aside>

  <!-- 右键菜单 -->
  <Teleport to="body">
    <div
      v-if="contextMenu.show"
      class="context-menu-overlay"
      @click="closeMenu"
      @contextmenu.prevent="closeMenu"
    >
      <div
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div class="menu-item" @click="togglePin">📌 置顶/取消置顶</div>
        <div class="menu-item" @click="toggleMute">🔕 免打扰/取消</div>
        <div class="menu-item danger" @click="deleteSession">🗑️ 删除会话</div>
      </div>
    </div>
  </Teleport>
</template>

<style lang="scss" src="./styles.scss" scoped />
<style>
.context-menu-overlay {
  position: fixed; inset: 0; z-index: 9999;
}
.context-menu {
  position: fixed; z-index: 10000;
  background: var(--bg-glass); backdrop-filter: blur(12px);
  border: 1px solid var(--border-light); border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg); padding: 4px; min-width: 160px;
}
.context-menu .menu-item {
  padding: 8px 16px; font-size: 13px; color: var(--font-main);
  cursor: pointer; border-radius: var(--radius-sm); transition: background var(--transition-fast);
}
.context-menu .menu-item:hover { background: var(--bg-hover); }
.context-menu .menu-item.danger { color: #ef4444; }
.context-menu .menu-item.danger:hover { background: rgba(239,68,68,0.1); }
.pin-icon, .mute-icon { font-size: 11px; margin-left: 4px; }
.info-top { display: flex; align-items: center; gap: 4px; }
.chat-message-item.pinned { background: var(--bg-hover); }
.badge-muted :deep(.el-badge__content) {
  background-color: var(--font-light) !important;
}
</style>
