<script setup lang="ts">
import { MsgEnum } from '@/enums'
import type { MsgType } from '@/services/types'
import Image from './image.vue'
import Voice from './voice.vue'
import File from './file.vue'
import Video from './video.vue'
import Text from './text.vue'
import Emoji from './emoji.vue'
import { useUserStore } from '@/stores/user'

const componentMap = {
  [MsgEnum.UNKNOWN]: '',
  [MsgEnum.TEXT]: Text,
  [MsgEnum.RECALL]: '',
  [MsgEnum.SYSTEM]: '',
  [MsgEnum.IMAGE]: Image,
  [MsgEnum.VOICE]: Voice,
  [MsgEnum.FILE]: File,
  [MsgEnum.VIDEO]: Video,
  [MsgEnum.EMOJI]: Emoji,
}

const userStore = useUserStore()

defineProps<{ message: MsgType; isBot?: boolean }>()
</script>

<template>
  <!-- draggable 用于"拖动消息到左侧会话转发"，但 draggable=true 会让浏览器把气泡内的
       鼠标拖动当成 HTML5 拖拽而不是文字选择，导致文本消息无法选中部分文字复制。
       文本消息的可选中优先级高于拖拽转发，所以仅对非文本类型保留拖拽 -->
  <component
    :is="componentMap[message.type]"
    :body="message.body"
    :is-bot="isBot"
    :data-message-id="message.id"
    :draggable="userStore.isSign && message.type !== MsgEnum.TEXT ? 'true' : 'false'"
  />
</template>
