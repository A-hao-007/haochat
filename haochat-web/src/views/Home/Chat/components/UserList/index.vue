<script setup lang="ts">
import { computed, ref, onUnmounted, onMounted, nextTick } from 'vue'
import { Plus, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { useGroupStore } from '@/stores/group'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import type { BaseUserItem } from '@/stores/cached'
import { RoleEnum } from '@/enums'
import UserItem from './UserItem/index.vue'

const groupListLastElRef = ref<HTMLDivElement>()
const groupStore = useGroupStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const groupUserList = computed(() => groupStore.userList)
const statistic = computed(() => groupStore.countInfo)

// 我在群里的昵称
const editingNickname = ref(false)
const nicknameDraft = ref('')
const startEditNickname = () => {
  nicknameDraft.value = statistic.value.nickname || ''
  editingNickname.value = true
}
const saveNickname = async () => {
  try {
    await groupStore.updateNickname(nicknameDraft.value.trim())
    editingNickname.value = false
    ElMessage.success('群昵称已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

onMounted(() => {
  let observer: IntersectionObserver
  nextTick(() => {
    observer = new IntersectionObserver(async (entries) => {
      if (entries?.[0]?.isIntersecting) {
        // 加载更多
        await groupStore.loadMore()
        // 停止观察该元素
        groupListLastElRef.value && observer.unobserve(groupListLastElRef.value)
        // 延迟500毫秒后，重新观察  groupListLastElRef 元素
        setTimeout(() => {
          groupListLastElRef.value && observer.observe(groupListLastElRef.value)
        }, 500)
      }
    })
    // 元素可见性监听
    groupListLastElRef.value && observer.observe(groupListLastElRef.value)
  })
  onUnmounted(() => {
    groupListLastElRef.value && observer.unobserve(groupListLastElRef.value)
  })
})

const hiddenGroupListShow = () => (groupStore.showGroupList = false)
const onAddGroupMember = () => {
  globalStore.createGroupModalInfo.show = true
  globalStore.createGroupModalInfo.isInvite = true
  // 禁用已经邀请的人
  globalStore.createGroupModalInfo.selectedUid = cachedStore.currentAtUsersList.map(
    (item: BaseUserItem) => item.uid,
  )
}
</script>

<template>
  <div class="user-list-box">
    <div
      class="user-list-mask"
      @click="hiddenGroupListShow"
      :class="groupStore.showGroupList ? 'show' : ''"
    />
    <div class="user-list-wrapper" :class="groupStore.showGroupList ? 'show' : ''">
      <div class="user-list-header">
        在线人数：{{ statistic.onlineNum || 0 }}
        <el-button
          v-login-show
          type="primary"
          :icon="Plus"
          circle
          :disabled="statistic.role === RoleEnum.REMOVED"
          size="small"
          @click="onAddGroupMember"
        />
      </div>
      <!-- 我在群里的昵称 -->
      <div v-login-show class="my-nickname-row">
        <template v-if="!editingNickname">
          <span class="my-nickname-label">我的群昵称：{{ statistic.nickname || '未设置' }}</span>
          <el-icon class="my-nickname-edit" :size="13" @click="startEditNickname"><Edit /></el-icon>
        </template>
        <template v-else>
          <el-input v-model="nicknameDraft" size="small" maxlength="20" placeholder="设置群昵称" @keyup.enter="saveNickname" />
          <el-button size="small" type="primary" @click="saveNickname">保存</el-button>
          <el-button size="small" @click="editingNickname = false">取消</el-button>
        </template>
      </div>
      <TransitionGroup
        v-show="groupUserList?.length"
        tag="ul"
        name="fade"
        class="user-list"
        v-loading.lock="groupStore.userListOptions.loading"
      >
        <UserItem v-for="user in groupUserList" :key="user.uid" :user="user" />
        <li key="visible_el" ref="groupListLastElRef">&nbsp;</li>
      </TransitionGroup>
      <template v-if="groupUserList?.length === 0">
        <div class="list-no-data">暂无成员~</div>
      </template>
      <!-- </ul> -->
    </div>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />

<style>
/* 1. 声明过渡效果 */
.fade-move,
.fade-enter-active,
.fade-leave-active {
  transition: all 0.5s cubic-bezier(0.55, 0, 0.1, 1);
}

/* 2. 声明进入和离开的状态 */
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scaleY(0.01) translate(30px, 0);
}

/* 3. 确保离开的项目被移除出了布局流
      以便正确地计算移动时的动画效果。 */
.fade-leave-active {
  position: absolute;
}
</style>
