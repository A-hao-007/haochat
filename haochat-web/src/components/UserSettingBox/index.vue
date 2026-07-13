<script setup lang="ts">
import { computed, reactive, ref, toRef, watchEffect } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Select,
  CloseBold,
  EditPen,
  Lock,
  Message,
  Edit,
  SwitchButton,
  Remove,
  Plus,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCachedStore } from '@/stores/cached'
import { useContactStore } from '@/stores/contacts'
import { SexEnum, IsYetEnum } from '@/enums'
import type { BadgeType } from '@/services/types'
import apis from '@/services/apis'
import { judgeClient } from '@/utils/detectDevice'
import { useRequest } from 'alova'
import { useModalBackGuard } from '@/hooks/useModalBackGuard'

const client = judgeClient()
const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue'])

const value = computed({
  get() {
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
  },
})

const userStore = useUserStore()
const cachedStore = useCachedStore()
const contactStore = useContactStore()

const userInfo = computed(() => userStore.userInfo)

const { send: handlerGetBadgeList, data: badgeList } = useRequest(apis.getBadgeList, {
  initialData: [],
  immediate: false,
})

// Stats
const ownedBadgeCount = computed(
  () => badgeList.value.filter((b) => b.obtain === IsYetEnum.YES).length,
)
const friendCount = computed(() => contactStore.contactsList?.length || 0)
const joinDays = computed(() => {
  const ct = (userInfo.value as any).createTime
  if (ct) return Math.max(1, Math.floor((Date.now() - new Date(ct).getTime()) / 86400000))
  return '-'
})

watchEffect(() => {
  if (value.value) {
    handlerGetBadgeList()
    userStore.getUserDetailAction()
    if (!contactStore.contactsList.length) {
      contactStore.getContactList(true).catch(() => {
        // Contact refresh is best-effort while opening the settings panel.
      })
    }
  }
})

const currentBadge = computed(() =>
  badgeList.value.find((b) => b.obtain === IsYetEnum.YES && b.wearing === IsYetEnum.YES),
)

const updateCurrentUserCache = (key: string, val: any) => {
  const uid = userStore.userInfo.uid
  if (uid && cachedStore.userCachedList[uid]) {
    ;(cachedStore.userCachedList[uid] as any)[key] = val
  }
}

// --- Toggle badge (wear / unwear) ---
const toggleBadge = async (badge: BadgeType) => {
  if (!badge?.id || badge.obtain !== IsYetEnum.YES) return
  // If currently worn, unequip by setting badgeId to 0 or null
  // The API setUserBadge toggles — let's check the backend
  await apis.setUserBadge(badge.id).send()
  handlerGetBadgeList()
  updateCurrentUserCache('wearingItemId', badge.id)
}

// --- Remove (unequip) badge ---
const removeBadge = async () => {
  // Unequip: set badgeId to 0
  await apis.setUserBadge(0).send()
  handlerGetBadgeList()
  if (userInfo.value.badge) userInfo.value.badge = undefined
  updateCurrentUserCache('wearingItemId', 0)
}

// --- Edit name ---
const editName = reactive({ isEdit: false, tempName: '', saving: false })
const onEditName = () => {
  if (!userInfo.value?.modifyNameChance || userInfo.value.modifyNameChance === 0) {
    ElMessage.warning('没有改名机会了哦~')
    return
  }
  editName.isEdit = true
  editName.tempName = userInfo.value.name || ''
}
const onSaveUserName = async () => {
  const n = editName.tempName?.trim()
  if (!n) {
    ElMessage.warning('用户名不能为空哦~')
    return
  }
  if (n === userInfo.value.name) {
    ElMessage.warning('用户名和当前一样的哦~')
    return
  }
  editName.saving = true
  try {
    await apis.modifyUserName(n).send()
    userStore.userInfo.name = n
    updateCurrentUserCache('name', n)
    if (userInfo.value.modifyNameChance && userInfo.value.modifyNameChance > 0) {
      userInfo.value.modifyNameChance = userInfo.value.modifyNameChance - 1
    }
    onCancelEditName()
  } catch (e: any) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    editName.saving = false
  }
}
const onCancelEditName = () => {
  editName.saving = false
  editName.isEdit = false
  editName.tempName = ''
}

// --- Avatar upload ---
const avatarFileInput = ref<HTMLInputElement>()
const uploadingAvatar = ref(false)
const triggerAvatarUpload = () => avatarFileInput.value?.click()
const compressAvatar = (file: File, max = 512, quality = 0.85): Promise<File> =>
  new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      let w = img.width,
        h = img.height
      if (w > max || h > max) {
        if (w >= h) {
          h = Math.round((h * max) / w)
          w = max
        } else {
          w = Math.round((w * max) / h)
          h = max
        }
      }
      const c = document.createElement('canvas')
      c.width = w
      c.height = h
      const ctx = c.getContext('2d')
      if (!ctx) {
        reject(new Error('canvas error'))
        return
      }
      ctx.drawImage(img, 0, 0, w, h)
      const out = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
      c.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error('compress fail'))
            return
          }
          const base = file.name.replace(/\.[^.]+$/, '')
          resolve(new File([blob], base + (out === 'image/png' ? '.png' : '.jpg'), { type: out }))
        },
        out,
        quality,
      )
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('read fail'))
    }
    img.src = url
  })

const onAvatarFileChange = async (e: Event) => {
  let file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    ;(e.target as HTMLInputElement).value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片不能超过10MB')
    ;(e.target as HTMLInputElement).value = ''
    return
  }
  uploadingAvatar.value = true
  let assetId: number | undefined
  const putToMinio = (url: string, uploadFile: File) =>
    new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('PUT', url, true)
      xhr.setRequestHeader('Content-Type', uploadFile.type)
      xhr.onload = () => (xhr.status === 200 ? resolve() : reject(new Error('Upload failed')))
      xhr.onerror = () => reject(new Error('Network error'))
      xhr.send(uploadFile)
    })
  try {
    if (file.size > 1024 * 1024) {
      try {
        file = await compressAvatar(file)
      } catch {
        /* keep original */
      }
    }
    const uploadFile = file
    const initResp = await apis
      .initFileUpload({
        scene: 'avatar',
        fileName: file.name,
        contentType: uploadFile.type || 'image/jpeg',
        size: uploadFile.size,
      })
      .send()
    assetId = initResp.assetId
    try {
      await putToMinio(initResp.uploadUrl, uploadFile)
      await apis.completeFileUpload(assetId).send()
    } catch (e) {
      const retryTicket = await apis.retryFileUpload(assetId).send()
      if (!retryTicket.downloadUrl) {
        if (!retryTicket.uploadUrl) throw e
        await putToMinio(retryTicket.uploadUrl, uploadFile)
        await apis.completeFileUpload(assetId).send()
      }
    }
    const { avatarUrl } = await apis.bindAvatarAsset(assetId).send()
    userStore.userInfo.avatar = avatarUrl
    updateCurrentUserCache('avatar', avatarUrl)
    ElMessage.success('头像已更新')
  } catch {
    if (assetId)
      await apis
        .cancelFileUpload(assetId)
        .send()
        .catch(() => undefined)
    ElMessage.error('上传失败，请检查网络或MinIO服务')
  } finally {
    uploadingAvatar.value = false
    ;(e.target as HTMLInputElement).value = ''
  }
}

// --- Password ---
const pwdDialog = reactive({
  show: false,
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
  saving: false,
})
const strongPwd = (p: string) => /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(p)
const openPwdDialog = () => {
  pwdDialog.oldPassword = ''
  pwdDialog.newPassword = ''
  pwdDialog.confirmPassword = ''
  pwdDialog.show = true
}
const onSavePassword = async () => {
  if (!pwdDialog.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!strongPwd(pwdDialog.newPassword)) {
    ElMessage.warning('新密码至少 8 位，含大小写字母和数字')
    return
  }
  if (pwdDialog.newPassword !== pwdDialog.confirmPassword) {
    ElMessage.warning('两次新密码不一致')
    return
  }
  pwdDialog.saving = true
  try {
    await apis
      .modifyPassword({ oldPassword: pwdDialog.oldPassword, newPassword: pwdDialog.newPassword })
      .send()
    ElMessage.success('密码修改成功')
    pwdDialog.show = false
  } catch (e: any) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    pwdDialog.saving = false
  }
}

// --- Email ---
const emailDialog = reactive({ show: false, email: '', code: '', sending: false, saving: false })
const hasEmail = computed(() => !!userInfo.value?.email)
const openEmailDialog = () => {
  emailDialog.email = ''
  emailDialog.code = ''
  emailDialog.show = true
}
const sendEmailCode = async () => {
  const email = emailDialog.email.trim()
  if (!email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  emailDialog.sending = true
  try {
    await apis.sendBindEmailCode({ email }).send()
    ElMessage.success('验证码已发送')
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    emailDialog.sending = false
  }
}
const onBindEmail = async () => {
  const email = emailDialog.email.trim()
  const code = emailDialog.code.trim()
  if (!code) {
    ElMessage.warning('请输入验证码')
    return
  }
  emailDialog.saving = true
  try {
    await apis.bindEmail({ email, code }).send()
    ElMessage.success('邮箱绑定成功')
    // Refresh user info to show new email
    userStore.getUserDetailAction()
    emailDialog.show = false
  } catch (e: any) {
    ElMessage.error(e?.message || '绑定失败')
  } finally {
    emailDialog.saving = false
  }
}

// --- Logout ---
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await apis
      .logout(localStorage.getItem('REFRESH_TOKEN') || undefined)
      .send()
      .catch(() => undefined)
    localStorage.removeItem('TOKEN')
    localStorage.removeItem('REFRESH_TOKEN')
    localStorage.removeItem('USER_INFO')
    window.location.reload()
  } catch {
    /* cancelled */
  }
}

// --- Badge img error ---
const onBadgeImgError = (e: Event) => {
  ;(e.target as HTMLElement).style.display = 'none'
}

// 浏览器“后退”优先关闭弹窗，而不是退出登录/系统
useModalBackGuard(value)
useModalBackGuard(toRef(pwdDialog, 'show'))
useModalBackGuard(toRef(emailDialog, 'show'))
</script>

<template>
  <ElDialog
    v-model="value"
    :width="client === 'PC' ? 520 : '88%'"
    :close-on-click-modal="true"
    center
    class="setting-glass"
  >
    <div class="setting-box">
      <!-- ===== Header: Avatar + Name + UID ===== -->
      <div class="profile-header">
        <div
          class="avatar-wrap"
          :class="{ uploading: uploadingAvatar }"
          @click="triggerAvatarUpload"
        >
          <ElAvatar
            :size="72"
            shape="circle"
            class="profile-avatar"
            :src="
              userInfo?.avatar ||
              'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
            "
          />
          <div v-if="uploadingAvatar" class="avatar-mask">上传中...</div>
          <el-icon
            v-if="userInfo.sex && [SexEnum.MAN, SexEnum.REMALE].includes(userInfo.sex)"
            size="16"
            class="avatar-sex"
            :style="{
              backgroundColor: userInfo.sex === SexEnum.MAN ? 'var(--el-color-primary)' : 'pink',
            }"
          >
            <IEpFemale v-if="userInfo.sex === SexEnum.MAN" />
            <IEpMale v-if="userInfo.sex === SexEnum.REMALE" />
          </el-icon>
        </div>
        <input
          ref="avatarFileInput"
          type="file"
          accept="image/*"
          hidden
          @change="onAvatarFileChange"
        />

        <div class="name-row">
          <template v-if="!editName.isEdit">
            <img
              v-if="currentBadge?.img"
              class="name-badge"
              :src="currentBadge.img"
              @error="onBadgeImgError"
            />
            <span class="profile-name">{{ userInfo.name || '-' }}</span>
            <el-tooltip
              :content="`剩余改名次数: ${userInfo.modifyNameChance || 0}`"
              placement="top"
            >
              <el-button
                size="small"
                :icon="EditPen"
                circle
                :disabled="!userInfo?.modifyNameChance || userInfo.modifyNameChance <= 0"
                @click="onEditName"
              />
            </el-tooltip>
          </template>
          <template v-else>
            <ElInput v-model="editName.tempName" maxlength="6" size="small" style="width: 140px" />
            <el-button
              size="small"
              type="primary"
              :icon="Select"
              circle
              :loading="editName.saving"
              @click="onSaveUserName"
            />
            <el-button
              size="small"
              type="danger"
              :icon="CloseBold"
              circle
              @click="onCancelEditName"
            />
          </template>
        </div>
        <div class="profile-uid">HaoChat ID: {{ userInfo.uid || '-' }}</div>

        <!-- Email display -->
        <div class="email-line" v-if="hasEmail">
          <span class="email-label">邮箱：</span>
          <span class="email-value">{{ userInfo.email }}</span>
          <el-button size="small" text type="primary" @click="openEmailDialog">切换</el-button>
        </div>
      </div>

      <!-- ===== Stats ===== -->
      <div class="stats-row">
        <div class="stat-card">
          <span class="stat-value">{{ joinDays }}</span>
          <span class="stat-label">加入天数</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">-</span>
          <span class="stat-label">消息数</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ friendCount }}</span>
          <span class="stat-label">好友数</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ ownedBadgeCount }}</span>
          <span class="stat-label">徽章</span>
        </div>
      </div>

      <!-- ===== Action buttons ===== -->
      <div class="action-buttons">
        <button class="action-btn" @click="openPwdDialog">
          <el-icon><Lock /></el-icon><span>修改密码</span>
        </button>
        <button class="action-btn" @click="openEmailDialog">
          <el-icon><Message /></el-icon><span>{{ hasEmail ? '切换邮箱' : '绑定邮箱' }}</span>
        </button>
        <button class="action-btn" @click="onEditName">
          <el-icon><Edit /></el-icon><span>编辑资料</span>
        </button>
        <button class="action-btn logout" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon><span>退出登录</span>
        </button>
      </div>

      <!-- ===== Badge section ===== -->
      <div class="badge-section" v-if="badgeList.length">
        <div class="section-title">
          我的徽章
          <el-button
            v-if="currentBadge"
            size="small"
            text
            type="danger"
            style="float: right"
            @click="removeBadge"
          >
            <el-icon><Remove /></el-icon> 卸下徽章
          </el-button>
        </div>
        <div class="badge-grid">
          <div
            v-for="badge in badgeList"
            :key="badge.id"
            class="badge-item"
            :class="{
              owned: badge.obtain === IsYetEnum.YES,
              active: badge.wearing === IsYetEnum.YES,
            }"
            @click="toggleBadge(badge)"
          >
            <img
              :src="badge.img"
              :alt="badge.describe"
              class="badge-img"
              :class="{ grayscale: badge.obtain !== IsYetEnum.YES }"
              @error="onBadgeImgError"
            />
            <div class="badge-mask" v-if="badge.obtain === IsYetEnum.YES">
              <template v-if="badge.wearing === IsYetEnum.YES">
                <el-icon color="#f56c6c"><Remove /></el-icon>
              </template>
              <template v-else>
                <el-icon color="var(--el-color-primary)"><Plus /></el-icon>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </ElDialog>

  <!-- Password dialog -->
  <ElDialog
    v-model="pwdDialog.show"
    title="修改密码"
    :width="client === 'PC' ? 420 : '85%'"
    :close-on-click-modal="false"
    append-to-body
    center
  >
    <div class="sub-form">
      <ElInput
        v-model="pwdDialog.oldPassword"
        type="password"
        show-password
        placeholder="原密码"
        autocomplete="current-password"
      />
      <ElInput
        v-model="pwdDialog.newPassword"
        type="password"
        show-password
        placeholder="新密码（8位以上，含大小写字母+数字）"
        autocomplete="new-password"
      />
      <ElInput
        v-model="pwdDialog.confirmPassword"
        type="password"
        show-password
        placeholder="确认新密码"
        autocomplete="new-password"
      />
    </div>
    <template #footer>
      <el-button @click="pwdDialog.show = false">取消</el-button>
      <el-button type="primary" :loading="pwdDialog.saving" @click="onSavePassword">确认</el-button>
    </template>
  </ElDialog>

  <!-- Email dialog -->
  <ElDialog
    v-model="emailDialog.show"
    :title="hasEmail ? '切换邮箱' : '绑定邮箱'"
    :width="client === 'PC' ? 420 : '85%'"
    :close-on-click-modal="false"
    append-to-body
    center
  >
    <div class="sub-form">
      <div v-if="hasEmail" class="current-email-hint">
        当前邮箱：<strong>{{ userInfo.email }}</strong>
      </div>
      <ElInput v-model="emailDialog.email" placeholder="请输入新邮箱" />
      <div class="email-row">
        <ElInput v-model="emailDialog.code" placeholder="验证码" />
        <el-button :loading="emailDialog.sending" @click="sendEmailCode">发送验证码</el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="emailDialog.show = false">取消</el-button>
      <el-button type="primary" :loading="emailDialog.saving" @click="onBindEmail">确认</el-button>
    </template>
  </ElDialog>
</template>

<style lang="scss" src="./styles.scss" scoped />
