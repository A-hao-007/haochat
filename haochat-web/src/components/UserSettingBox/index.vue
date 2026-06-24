<script setup lang="ts">
import { computed, reactive, ref, watchEffect } from 'vue'
import { useRequest } from 'alova'
import { ElMessage } from 'element-plus'
import { Select, CloseBold, EditPen } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCachedStore } from '@/stores/cached'
import { SexEnum, IsYetEnum } from '@/enums'
import type { BadgeType } from '@/services/types'
import apis from '@/services/apis'
import { judgeClient } from '@/utils/detectDevice'

const client = judgeClient()

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue'])

const value = computed({
  get() {
    return props.modelValue
  },
  set(value) {
    emit('update:modelValue', value)
  },
})

const editName = reactive({
  isEdit: false,
  tempName: '',
  saving: false,
})

const userStore = useUserStore()
const cachedStore = useCachedStore()

const userInfo = computed(() => userStore.userInfo)
const { send: handlerGetBadgeList, data: badgeList } = useRequest(apis.getBadgeList, {
  initialData: [],
  immediate: false,
})

watchEffect(() => {
  if (value.value) {
    handlerGetBadgeList()
    userStore.getUserDetailAction()
  }
})

const currentBadge = computed(() =>
  badgeList.value.find((item) => item.obtain === IsYetEnum.YES && item.wearing === IsYetEnum.YES),
)

// 更新缓存里面的用户信息
const updateCurrentUserCache = (key: 'name' | 'wearingItemId' | 'avatar', value: any) => {
  const currentUser = userStore.userInfo.uid && cachedStore.userCachedList[userStore.userInfo.uid]
  if (currentUser) {
    currentUser[key] = value // 更新缓存里面的用户信息
  }
}

// 佩戴卸下徽章
const toggleWarningBadge = async (badge: BadgeType) => {
  if (!badge?.id) return
  await apis.setUserBadge(badge.id).send()
  handlerGetBadgeList()
  badge.img && (userInfo.value.badge = badge.img)
  updateCurrentUserCache('wearingItemId', badge.id) // 更新缓存里面的用户徽章
}

// 编辑用户名
const onEditName = () => {
  if (!userInfo.value?.modifyNameChance || userInfo.value.modifyNameChance === 0) return
  editName.isEdit = true
  editName.tempName = userInfo.value.name || ''
}

// 确认保存用户名
const onSaveUserName = async () => {
  if (!editName.tempName || editName.tempName.trim() === '') {
    ElMessage.warning('用户名不能为空哦~')
    return
  }
  if (editName.tempName === userInfo.value.name) {
    ElMessage.warning('用户名和当前一样的哦~')
    return
  }
  editName.saving = true

  await apis.modifyUserName(editName.tempName).send() // 更改用户名
  userStore.userInfo.name = editName.tempName // 更新用户信息里面的用户名
  updateCurrentUserCache('name', editName.tempName) // 更新缓存里面的用户信息
  // 重置状态
  onCancelEditName()
  // 没有更名机会就不走下去
  if (!userInfo.value?.modifyNameChance || userInfo.value.modifyNameChance === 0) return
  userInfo.value.modifyNameChance = userInfo.value?.modifyNameChance - 1 // 减少更名次数
}
// 确认保存用户名
const onCancelEditName = async () => {
  editName.saving = false
  editName.isEdit = false
  editName.tempName = ''
}

// 更换头像
const avatarFileInput = ref<HTMLInputElement>()
const uploadingAvatar = ref(false)

const triggerAvatarUpload = () => {
  avatarFileInput.value?.click()
}

const onAvatarFileChange = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不能超过5MB'); return }
  uploadingAvatar.value = true
  try {
    // 通过 MinIO OSS 上传
    const { uploadUrl, downloadUrl } = await apis.getUploadUrl({ fileName: file.name, scene: 1 }).send()
    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('PUT', uploadUrl, true)
      xhr.setRequestHeader('Content-Type', file.type)
      xhr.onload = () => xhr.status === 200 ? resolve() : reject(new Error('Upload failed'))
      xhr.onerror = () => reject(new Error('Network error'))
      xhr.send(file)
    })
    await apis.updateAvatar(downloadUrl).send()
    userInfo.value.avatar = downloadUrl
    updateCurrentUserCache('avatar', downloadUrl)
    ElMessage.success('头像已更新')
  } catch { ElMessage.error('上传失败，请确保MinIO服务已启动') }
  finally { uploadingAvatar.value = false; (e.target as HTMLInputElement).value = '' }
}
</script>

<template>
  <ElDialog
    class="setting-box-modal"
    v-model="value"
    :width="client === 'PC' ? 580 : '85%'"
    :close-on-click-modal="false"
    center
  >
    <div class="setting-box">
      <div class="setting-avatar-box">
        <ElAvatar
          size="large"
          class="setting-avatar"
          :src="userInfo?.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'"
          @click="triggerAvatarUpload"
          style="cursor:pointer"
          :class="{ uploading: uploadingAvatar }"
        />
        <div v-if="uploadingAvatar" class="avatar-uploading">上传中...</div>
        <p class="avatar-hint">点击头像更换图片（支持拖拽）</p>
        <input ref="avatarFileInput" type="file" accept="image/*" hidden @change="onAvatarFileChange" />
        <el-icon
          size="20"
          color="var(--font-main)"
          class="setting-avatar-sex"
          v-if="userInfo.sex && [SexEnum.MAN, SexEnum.REMALE].includes(userInfo.sex)"
          :style="{
            backgroundColor: `var(${
              userInfo.sex === SexEnum.MAN ? '--avatar-sex-bg-man' : '--avatar-sex-bg-female'
            })`,
          }"
        >
          <IEpFemale v-if="userInfo.sex === SexEnum.MAN" />
          <IEpMale v-if="userInfo.sex === SexEnum.REMALE" />
        </el-icon>
      </div>

      <div class="setting-name">
        <div class="name-edit-wrapper" v-show="editName.isEdit === false">
          <span class="user-name">
            <el-tooltip effect="dark" :content="currentBadge?.describe" placement="top">
              <img class="setting-badge" :src="currentBadge?.img" v-show="currentBadge" />
            </el-tooltip>
            {{ userInfo.name || '-' }}
          </span>
          <el-tooltip
            class="box-item"
            effect="dark"
            :content="`剩余改名次数: ${userInfo.modifyNameChance || 0}`"
            placement="right"
          >
            <el-button
              class="name-edit-icon"
              size="small"
              :class="
                userInfo?.modifyNameChance && userInfo.modifyNameChance > 0
                  ? 'pointer'
                  : 'not-allow is-disabled'
              "
              :icon="EditPen"
              circle
              @click="onEditName"
            />
          </el-tooltip>
        </div>
        <div class="name-edit-wrapper" v-show="editName.isEdit">
          <ElInput type="text" v-model="editName.tempName" maxlength="6" />
          <el-button
            class="name-edit-icon"
            size="small"
            type="primary"
            :icon="Select"
            circle
            @click="onSaveUserName"
          />
          <el-button
            class="name-edit-icon"
            size="small"
            type="danger"
            :icon="CloseBold"
            circle
            @click="onCancelEditName"
          />
        </div>
      </div>

      <el-alert
        class="setting-tips"
        title="Tips: HaoChat名称不允许重复，快来抢占"
        type="warning"
        :closable="false"
      />

      <ul class="badge-list">
        <li class="badge-item" v-for="badge of badgeList" :key="badge.id">
          <img
            class="badge-item-icon"
            :class="{ 'badge-item-icon-has': badge.obtain === IsYetEnum.YES }"
            :src="badge.img"
            alt="badge"
          />
          <div class="badge-item-mask">
            <template v-if="badge.obtain === IsYetEnum.YES">
              <el-button
                size="small"
                v-if="badge.wearing === IsYetEnum.NO"
                @click="toggleWarningBadge(badge)"
              >
                佩戴
              </el-button>
              <!-- <el-button size="small" v-if="badge.wearing === IsYetEnum.YES">卸下</el-button> -->
            </template>
            <el-tooltip effect="dark" :content="badge.describe" placement="top">
              <el-icon class="badge-item-info" color="var(--font-main)"><IEpInfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </li>
      </ul>
    </div>
  </ElDialog>
</template>

<style lang="scss" src="./styles.scss" scoped />
