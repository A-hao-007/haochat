<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useLikeToggle } from '@/hooks/useLikeToggle'
import { useChatStore } from '@/stores/chat'
import type { MessageType } from '@/services/types'
import eventBus from '@/utils/eventBus'
import { copyToClip } from '@/utils/copy'
import { MsgEnum } from '@/enums'

const props = defineProps<{ msg: MessageType }>()

const chatStore = useChatStore()

const { isLike, isDisLike, onLike, onDisLike } = useLikeToggle(props.msg.message)

/**
 * 回复消息
 */
const onReplyMsg = async (msgFromUser: MessageType) => {
  if (!msgFromUser) return
  chatStore.currentMsgReply = msgFromUser
  eventBus.emit('focusMsgInput')
}

/** 复制文本消息内容（AI 回复常需要整段复制，右键菜单里也有，这里是快捷入口） */
const onCopyMsg = () => {
  const content = props.msg.message.body?.content
  if (!content) return
  copyToClip(content)
  ElMessage.success('复制成功~')
}
</script>

<template>
  <div class="msg-option" v-if="!msg.loading">
    <span
      v-if="msg.message.type === MsgEnum.TEXT"
      class="msg-option-item"
      title="复制"
      @click="onCopyMsg"
    >
      <Icon icon="copy" :size="13" />
    </span>
    <span class="msg-option-item" title="回复">
      <Icon icon="reply" :size="14" v-login="() => onReplyMsg(msg)" />
    </span>
    <span class="msg-option-item" title="点赞">
      <Icon icon="like" :size="14" :class="[{ 'like-active': isLike }]" v-login="() => onLike()" />
    </span>
    <span class="msg-option-item" title="举报">
      <Icon
        icon="dislike"
        :size="15"
        :class="[{ 'dislike-active': isDisLike }]"
        v-login="() => onDisLike()"
      />
    </span>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
