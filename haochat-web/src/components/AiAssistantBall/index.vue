<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import apis from '@/services/apis'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import { RoomTypeEnum } from '@/enums'

const AI_UID = 10001
const BALL_SIZE = 58
const EDGE = 12
const STORAGE_KEY = 'HAOCHAT_AI_BALL_POSITION'

const router = useRouter()
const userStore = useUserStore()
const globalStore = useGlobalStore()
const chatStore = useChatStore()
const loading = ref(false)
const expanded = ref(false)
const ready = ref(false)
const position = ref({ x: 0, y: 0 })

let activePointerId: number | null = null
let startPointer = { x: 0, y: 0 }
let startPosition = { x: 0, y: 0 }
let moved = false

const clampPosition = (x: number, y: number) => ({
  x: Math.min(Math.max(EDGE, x), Math.max(EDGE, window.innerWidth - BALL_SIZE - EDGE)),
  y: Math.min(Math.max(EDGE, y), Math.max(EDGE, window.innerHeight - BALL_SIZE - EDGE)),
})

const savePosition = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(position.value))
}

const restorePosition = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
      position.value = clampPosition(saved.x, saved.y)
    } else {
      position.value = clampPosition(window.innerWidth - 94, window.innerHeight - 110)
    }
  } catch {
    position.value = clampPosition(window.innerWidth - 94, window.innerHeight - 110)
  }
  ready.value = true
}

const ballStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`,
}))

const panelStyle = computed(() => {
  const panelWidth = 224
  const panelHeight = 248
  const left =
    position.value.x > window.innerWidth / 2
      ? position.value.x - panelWidth - 12
      : position.value.x + BALL_SIZE + 12
  const top = Math.min(
    Math.max(EDGE, position.value.y - panelHeight / 2 + BALL_SIZE / 2),
    Math.max(EDGE, window.innerHeight - panelHeight - EDGE),
  )
  return { left: `${Math.max(EDGE, left)}px`, top: `${top}px` }
})

const onPointerMove = (event: PointerEvent) => {
  if (activePointerId !== event.pointerId) return
  const dx = event.clientX - startPointer.x
  const dy = event.clientY - startPointer.y
  if (Math.hypot(dx, dy) > 4) moved = true
  if (moved) {
    expanded.value = false
    position.value = clampPosition(startPosition.x + dx, startPosition.y + dy)
  }
}

const finishPointer = (event: PointerEvent) => {
  if (activePointerId !== event.pointerId) return
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', finishPointer)
  window.removeEventListener('pointercancel', finishPointer)
  activePointerId = null
  if (moved) savePosition()
  else expanded.value = !expanded.value
}

const startDrag = (event: PointerEvent) => {
  if (event.button !== 0) return
  event.preventDefault()
  activePointerId = event.pointerId
  startPointer = { x: event.clientX, y: event.clientY }
  startPosition = { ...position.value }
  moved = false
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', finishPointer)
  window.addEventListener('pointercancel', finishPointer)
}

const openAi = async () => {
  expanded.value = false
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
    if (router.currentRoute.value.path !== '/') await router.push('/')
  } catch (e: any) {
    ElMessage.error(e?.message || '无法打开 AI 助手')
  } finally {
    loading.value = false
  }
}

const goTo = (target: 'messages' | 'add-friend' | 'groups') => {
  expanded.value = false
  if (target === 'messages') router.push('/')
  if (target === 'add-friend') router.push({ path: '/contact', query: { add: '1' } })
  if (target === 'groups') router.push('/groups')
}

const resetPosition = () => {
  position.value = clampPosition(window.innerWidth - 94, window.innerHeight - 110)
  savePosition()
  expanded.value = false
  ElMessage.success('悬浮助手已回到默认位置')
}

const handleResize = () => {
  position.value = clampPosition(position.value.x, position.value.y)
  savePosition()
}

onMounted(() => {
  restorePosition()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', finishPointer)
  window.removeEventListener('pointercancel', finishPointer)
})
</script>

<template>
  <div v-if="ready" class="ai-assistant-root">
    <transition name="assistant-panel">
      <aside v-if="expanded" class="assistant-panel" :style="panelStyle">
        <div class="panel-heading">
          <div><strong>快捷助手</strong><span>选择要执行的操作</span></div>
          <button type="button" aria-label="关闭" @click="expanded = false"><IEpClose :size="14" /></button>
        </div>
        <button class="primary-entry" type="button" @click="openAi">
          <span class="entry-icon ai">🤖</span>
          <span><strong>AI 助手</strong><small>提问、总结与智能聊天</small></span>
          <IEpArrowRight :size="15" />
        </button>
        <div class="quick-grid">
          <button type="button" @click="goTo('messages')"><IEpChatDotRound :size="18" /><span>消息</span></button>
          <button type="button" @click="goTo('add-friend')"><IEpUserFilled :size="18" /><span>添加好友</span></button>
          <button type="button" @click="goTo('groups')"><IEpUser :size="18" /><span>群组</span></button>
        </div>
        <button class="reset-entry" type="button" @click="resetPosition"><IEpRefreshLeft :size="14" />恢复默认位置</button>
      </aside>
    </transition>

    <button
      class="ai-ball"
      :class="{ loading, expanded }"
      :style="ballStyle"
      type="button"
      title="拖动可移动，点击打开快捷功能"
      aria-label="快捷助手"
      @pointerdown="startDrag"
    >
      <span class="drag-hint"><IEpRank :size="12" /></span>
      <span class="ai-ball-icon">🤖</span>
      <span class="status-dot"></span>
    </button>
  </div>
</template>

<style scoped>
.ai-ball { position: fixed; z-index: 2200; display: flex; align-items: center; justify-content: center; width: 58px; height: 58px; touch-action: none; user-select: none; cursor: grab; background: linear-gradient(135deg, #2f66e8, #5b8bff); border: 0; border-radius: 50%; box-shadow: 0 12px 30px rgba(47,102,232,.36); transition: box-shadow .2s, transform .2s; }
.ai-ball:hover, .ai-ball.expanded { box-shadow: 0 16px 38px rgba(47,102,232,.46); transform: scale(1.05); }
.ai-ball:active { cursor: grabbing; transform: scale(.98); }
.ai-ball.loading { opacity: .72; }
.ai-ball-icon { font-size: 27px; line-height: 1; pointer-events: none; }
.drag-hint { position: absolute; top: -5px; left: -5px; display: flex; align-items: center; justify-content: center; width: 20px; height: 20px; color: #4a72da; pointer-events: none; background: #fff; border-radius: 50%; box-shadow: 0 3px 9px rgba(15,23,42,.16); }
.status-dot { position: absolute; right: 3px; bottom: 3px; width: 10px; height: 10px; pointer-events: none; background: #34d399; border: 2px solid #fff; border-radius: 50%; }
.assistant-panel { position: fixed; z-index: 2199; box-sizing: border-box; width: 224px; padding: 13px; color: #1f2937; background: rgba(255,255,255,.97); border: 1px solid rgba(203,213,225,.8); border-radius: 15px; box-shadow: 0 20px 55px rgba(15,23,42,.2); backdrop-filter: blur(14px); }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 11px; }
.panel-heading strong, .panel-heading span { display: block; }
.panel-heading strong { margin-bottom: 2px; font-size: 13px; }
.panel-heading span { font-size: 9px; color: #94a3b8; }
.panel-heading button { display: flex; padding: 4px; color: #94a3b8; cursor: pointer; background: #f1f5f9; border: 0; border-radius: 6px; }
.primary-entry { display: flex; gap: 9px; align-items: center; width: 100%; padding: 9px; color: #1f2937; text-align: left; cursor: pointer; background: #eff6ff; border: 1px solid #dbeafe; border-radius: 10px; }
.entry-icon { display: flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 34px; height: 34px; font-size: 18px; background: #fff; border-radius: 9px; }
.primary-entry > span:nth-child(2) { flex: 1; min-width: 0; }
.primary-entry strong, .primary-entry small { display: block; }
.primary-entry strong { margin-bottom: 2px; font-size: 11px; }
.primary-entry small { font-size: 8px; color: #718096; white-space: nowrap; }
.primary-entry > svg { color: #3b82f6; }
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 9px; }
.quick-grid button { display: flex; flex-direction: column; gap: 5px; align-items: center; justify-content: center; padding: 9px 3px; font-size: 9px; color: #526179; cursor: pointer; background: #f8fafc; border: 1px solid #edf1f5; border-radius: 9px; }
.quick-grid button:hover { color: #2563eb; background: #f1f6ff; border-color: #dbeafe; }
.reset-entry { display: flex; gap: 5px; align-items: center; justify-content: center; width: 100%; padding: 7px 0 1px; font-size: 8px; color: #94a3b8; cursor: pointer; background: transparent; border: 0; }
.assistant-panel-enter-active, .assistant-panel-leave-active { transition: opacity .16s ease, transform .16s ease; }
.assistant-panel-enter-from, .assistant-panel-leave-to { opacity: 0; transform: scale(.95); }
@media (max-width: 768px) { .ai-ball { width: 52px; height: 52px; } .ai-ball-icon { font-size: 24px; } .assistant-panel { width: 216px; } }
</style>
