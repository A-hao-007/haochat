<script setup lang="ts">
import { computed, toRef } from 'vue'
import apis from '@/services/apis'
import { ContextMenu, ContextMenuItem, type MenuOptions } from '@imengyu/vue3-context-menu'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useGroupStore } from '@/stores/group'
import { PowerEnum } from '@/enums'
import eventBus from '@/utils/eventBus'
import { RoleEnum } from '@/enums'

const props = defineProps<{
  // 消息体
  uid: number
  // 菜单设置-其它的参数透传
  options?: MenuOptions
}>()

const uid = toRef(props.uid)

const userInfo = useUserStore()?.userInfo
const globalStore = useGlobalStore()
const groupStore = useGroupStore()
const statistic = computed(() => groupStore.countInfo)
const isMe = computed(() => userInfo?.uid === uid.value)
const isAdmin = computed(() => userInfo?.power === PowerEnum.ADMIN)
const onAtUser = (uid: number, ignoreCheck: boolean) =>
  eventBus.emit('onSelectPerson', { uid, ignoreCheck })

// 拉黑用户
const onBlockUser = async () => {
  const uid = props.uid
  if (uid) {
    await apis.blockUser({ uid }).send()
  }
}
// 添加好友
const onAddFriend = async () => {
  globalStore.addFriendModalInfo.show = true
  globalStore.addFriendModalInfo.uid = props.uid
}
// 移除群成员
const onRemoveMember = async () => {
  await apis.removeGroupMember({ uid: props.uid, roomId: globalStore.currentSession.roomId }).send()
  // 更新群成员列表
  groupStore.getGroupUserList(true)
}

// 转让群主
const onTransferLord = async () => {
  try {
    await ElMessageBox.confirm('转让后你将变为管理员，确定转让群主吗？', '提示', {
      confirmButtonText: '确定转让',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await groupStore.transferLord(props.uid)
    ElMessage.success('群主已转让')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '转让失败')
  }
}
</script>

<template>
  <ContextMenu :options="{ theme: 'dark', x: 0, y: 0, ...props.options }">
    <ContextMenuItem label="艾特Ta" @click="onAtUser?.(props.uid, true)" v-login-show>
      <template #icon> <span class="icon">@</span> </template>
    </ContextMenuItem>
    <ContextMenuItem v-if="isAdmin" label="拉黑(管理)" @click="onBlockUser">
      <template #icon>
        <Icon icon="lahei" :size="13" />
      </template>
    </ContextMenuItem>
    <ContextMenuItem vLoginShow v-friends="uid" label="添加好友" @click="onAddFriend">
      <template #icon>
        <Icon icon="tianjia" :size="13" />
      </template>
    </ContextMenuItem>
    <!-- 群主和管理员才能踢人（踢的不是自己） -->
    <ContextMenuItem
      vLoginShow
      v-if="[RoleEnum.LORD, RoleEnum.ADMIN].includes(statistic.role) && !isMe"
      label="剔出群聊"
      @click="onRemoveMember"
    >
      <template #icon>
        <Icon icon="yichu" :size="13" />
      </template>
    </ContextMenuItem>
    <!-- 只有群主才能转让群主（不能转让给自己） -->
    <ContextMenuItem
      vLoginShow
      v-if="statistic.role === RoleEnum.LORD && !isMe"
      label="转让群主"
      @click="onTransferLord"
    >
      <template #icon>
        <span class="icon">👑</span>
      </template>
    </ContextMenuItem>
  </ContextMenu>
</template>

<style lang="scss" src="./styles.scss" />
