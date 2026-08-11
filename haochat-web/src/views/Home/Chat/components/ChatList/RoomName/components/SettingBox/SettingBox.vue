<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import apis from '@/services/apis'
import { useCachedStore } from '@/stores/cached'
import { useGlobalStore } from '@/stores/global'
import { useGroupStore } from '@/stores/group'
import { useUserStore } from '@/stores/user'
import { OnlineEnum, RoleEnum } from '@/enums'

const emit = defineEmits<{ close: [] }>()
const groupStore = useGroupStore()
const globalStore = useGlobalStore()
const userStore = useUserStore()
const cachedStore = useCachedStore()

const loading = ref(true)
const actionUid = ref<number | null>(null)
const activeTab = ref<'overview' | 'members'>('overview')
const keyword = ref('')
const nickname = ref('')
const notice = ref('')
const savingNickname = ref(false)
const savingNotice = ref(false)
const exiting = ref(false)

const detail = computed(() => groupStore.countInfo)
const currentUid = computed(() => Number(userStore.userInfo.uid || 0))
const myRole = computed(() => detail.value.role)
const isLord = computed(() => myRole.value === RoleEnum.LORD)
const canManage = computed(() => [RoleEnum.LORD, RoleEnum.ADMIN].includes(myRole.value))

const members = computed(() =>
  groupStore.userList.map((member) => {
    const cached = cachedStore.userCachedList[member.uid] || {}
    return {
      ...cached,
      ...member,
      name: member.nickname || member.name || cached.name || `用户 ${member.uid}`,
      accountName: member.name || cached.name || '',
      avatar: member.avatar || cached.avatar || '',
      roleId: member.roleId || RoleEnum.NORMAL,
    }
  }),
)

const filteredMembers = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return members.value
  return members.value.filter((member) =>
    `${member.name} ${member.accountName} ${member.uid}`.toLowerCase().includes(key),
  )
})

const roleName = (role?: number) => {
  if (role === RoleEnum.LORD) return '群主'
  if (role === RoleEnum.ADMIN) return '管理员'
  return '成员'
}

const roleClass = (role?: number) => {
  if (role === RoleEnum.LORD) return 'lord'
  if (role === RoleEnum.ADMIN) return 'admin'
  return 'member'
}

const canOperateMember = (member: any) => {
  if (!canManage.value || member.uid === currentUid.value || member.roleId === RoleEnum.LORD) return false
  if (isLord.value) return true
  return member.roleId === RoleEnum.NORMAL
}

const refresh = async () => {
  loading.value = true
  try {
    await Promise.all([groupStore.getCountStatistic(), groupStore.getGroupUserList(true)])
    nickname.value = groupStore.countInfo.nickname || ''
    notice.value = groupStore.countInfo.notice || ''
  } finally {
    loading.value = false
  }
}

const openInvite = () => {
  globalStore.createGroupModalInfo.selectedUid = groupStore.userList.map((item) => item.uid)
  globalStore.createGroupModalInfo.isInvite = true
  globalStore.createGroupModalInfo.show = true
}

const saveNickname = async () => {
  savingNickname.value = true
  try {
    await groupStore.updateNickname(nickname.value.trim())
    ElMessage.success('群昵称已更新')
  } catch (error: any) {
    ElMessage.error(error?.message || '群昵称更新失败')
  } finally {
    savingNickname.value = false
  }
}

const saveNotice = async () => {
  if (!canManage.value) return
  savingNotice.value = true
  try {
    await groupStore.updateNotice(notice.value.trim())
    ElMessage.success('群公告已发布')
  } catch (error: any) {
    ElMessage.error(error?.message || '群公告发布失败')
  } finally {
    savingNotice.value = false
  }
}

const setAdmin = async (member: any) => {
  actionUid.value = member.uid
  try {
    if (member.roleId === RoleEnum.ADMIN) {
      await groupStore.revokeAdmin([member.uid])
      ElMessage.success('已撤销管理员')
    } else {
      if (groupStore.adminUidList.length >= 3) {
        ElMessage.warning('最多只能设置 3 位管理员')
        return
      }
      await groupStore.addAdmin([member.uid])
      ElMessage.success('管理员设置成功')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    actionUid.value = null
  }
}

const muteMember = async (member: any) => {
  try {
    const result = (await ElMessageBox.prompt(
      `请输入禁言 ${member.name} 的分钟数（1-43200）`,
      '设置成员禁言',
      {
        confirmButtonText: '确认禁言',
        cancelButtonText: '取消',
        inputValue: '10',
        inputPattern: /^(?:[1-9]|[1-9]\d{1,3}|[1-3]\d{4}|4[0-2]\d{3}|43[01]\d{2}|43200)$/,
        inputErrorMessage: '请输入 1 到 43200 之间的整数',
      },
    )) as any
    actionUid.value = member.uid
    await apis
      .muteGroupMember({
        roomId: globalStore.currentSession.roomId,
        uid: member.uid,
        minutes: Number(result.value),
      })
      .send()
    ElMessage.success(`已禁言 ${result.value} 分钟`)
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '禁言失败')
  } finally {
    actionUid.value = null
  }
}

const unmuteMember = async (member: any) => {
  actionUid.value = member.uid
  try {
    await apis
      .unmuteGroupMember({ roomId: globalStore.currentSession.roomId, uid: member.uid })
      .send()
    ElMessage.success('已解除禁言')
  } catch (error: any) {
    ElMessage.error(error?.message || '解除禁言失败')
  } finally {
    actionUid.value = null
  }
}

const removeMember = async (member: any) => {
  try {
    await ElMessageBox.confirm(`确定将 ${member.name} 移出群聊吗？`, '移除成员', {
      confirmButtonText: '移出群聊',
      cancelButtonText: '取消',
      type: 'warning',
    })
    actionUid.value = member.uid
    await apis
      .removeGroupMember({ roomId: globalStore.currentSession.roomId, uid: member.uid })
      .send()
    await groupStore.getGroupUserList(true)
    ElMessage.success('成员已移出群聊')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '移除失败')
  } finally {
    actionUid.value = null
  }
}

const transferOwner = async (member: any) => {
  try {
    await ElMessageBox.confirm(
      `确认将群主转让给 ${member.name}？转让后你将成为管理员。`,
      '转让群主',
      {
        confirmButtonText: '确认转让',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    actionUid.value = member.uid
    await groupStore.transferLord(member.uid)
    ElMessage.success('群主已转让')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '转让失败')
  } finally {
    actionUid.value = null
  }
}

const exitGroup = async () => {
  try {
    await ElMessageBox.confirm(
      isLord.value ? '群主退出前需要先转让群主。确定仍要尝试退出吗？' : '退出后将不再接收本群消息，确定退出吗？',
      '退出群聊',
      {
        confirmButtonText: '确认退出',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    exiting.value = true
    await groupStore.exitGroup()
    emit('close')
    ElMessage.success('已退出群聊')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '退出失败')
  } finally {
    exiting.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="group-settings" v-loading="loading">
    <header class="settings-header">
      <div>
        <p class="eyebrow">群组设置</p>
        <h2>群组管理</h2>
        <p>群资料、成员权限与安全操作集中管理</p>
      </div>
      <button class="close-button" type="button" aria-label="关闭" @click="emit('close')">
        <IEpClose :size="18" />
      </button>
    </header>

    <div class="group-card">
      <Avatar :src="detail.avatar" :size="58" />
      <div class="group-copy">
        <h3>{{ detail.groupName || '未命名群聊' }}</h3>
        <p>群号 {{ detail.roomId }} · {{ members.length }} 位成员 · {{ detail.onlineNum || 0 }} 人在线</p>
      </div>
      <span :class="['my-role', roleClass(myRole)]">{{ roleName(myRole) }}</span>
    </div>

    <nav class="setting-tabs">
      <button :class="{ active: activeTab === 'overview' }" type="button" @click="activeTab = 'overview'">
        群资料
      </button>
      <button :class="{ active: activeTab === 'members' }" type="button" @click="activeTab = 'members'">
        成员管理 <span>{{ members.length }}</span>
      </button>
    </nav>

    <div v-if="activeTab === 'overview'" class="tab-content overview-content">
      <article class="setting-card">
        <div class="card-heading">
          <span class="heading-icon blue"><IEpUser /></span>
          <div><h4>我的群昵称</h4><p>只在当前群聊中展示，不影响账号昵称</p></div>
        </div>
        <div class="field-row">
          <el-input v-model="nickname" maxlength="20" show-word-limit placeholder="输入群昵称" />
          <el-button type="primary" :loading="savingNickname" @click="saveNickname">保存</el-button>
        </div>
      </article>

      <article class="setting-card">
        <div class="card-heading">
          <span class="heading-icon blue"><IEpBell /></span>
          <div><h4>群公告</h4><p>{{ canManage ? '群主和管理员可以发布公告' : '仅群主和管理员可以修改' }}</p></div>
        </div>
        <el-input
          v-model="notice"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          resize="none"
          :readonly="!canManage"
          placeholder="向群成员发布一条公告…"
        />
        <div v-if="canManage" class="card-actions">
          <el-button type="primary" :loading="savingNotice" @click="saveNotice">发布公告</el-button>
        </div>
      </article>

      <article class="setting-card invite-card">
        <div class="card-heading">
          <span class="heading-icon blue"><IEpPlus /></span>
          <div><h4>邀请好友入群</h4><p>从好友列表选择成员，已在群中的账号会自动禁选</p></div>
        </div>
        <button class="secondary-action" type="button" @click="openInvite">
          <IEpPlus :size="15" />
          <span>邀请成员</span>
        </button>
      </article>

      <article class="setting-card danger-card">
        <div class="card-heading">
          <span class="heading-icon red"><IEpWarning /></span>
          <div><h4>退出群聊</h4><p>{{ isLord ? '群主需先将群主转让给其他成员' : '退出后可由群成员再次邀请加入' }}</p></div>
        </div>
        <el-button type="danger" plain :loading="exiting" @click="exitGroup">退出群聊</el-button>
      </article>
    </div>

    <div v-else class="tab-content members-content">
      <div class="member-toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索昵称、账号名或 UID">
          <template #prefix><IEpSearch /></template>
        </el-input>
        <el-button type="primary" @click="openInvite"><IEpPlus /> 邀请</el-button>
      </div>

      <div class="permission-note">
        <IEpInfoFilled />
        <span v-if="isLord">你可以设置管理员、禁言、移除成员和转让群主。</span>
        <span v-else-if="canManage">你可以禁言或移除普通成员。</span>
        <span v-else>你可以查看成员列表；管理操作仅对群主和管理员开放。</span>
      </div>

      <div class="member-list">
        <article v-for="member in filteredMembers" :key="member.uid" class="member-row">
          <div class="member-profile">
            <div class="avatar-wrap">
              <Avatar :src="member.avatar" :size="42" />
              <i :class="{ online: member.activeStatus === OnlineEnum.ONLINE }"></i>
            </div>
            <div class="member-copy">
              <div class="member-name">
                <strong>{{ member.name }}</strong>
                <span v-if="member.uid === currentUid" class="me-tag">我</span>
                <span :class="['role-tag', roleClass(member.roleId)]">{{ roleName(member.roleId) }}</span>
              </div>
              <p>UID {{ member.uid }}<span v-if="member.accountName && member.accountName !== member.name"> · {{ member.accountName }}</span></p>
            </div>
          </div>

          <div v-if="canOperateMember(member)" class="member-actions">
            <el-dropdown trigger="click">
              <button class="more-button" type="button" :disabled="actionUid === member.uid">
                <IEpMoreFilled />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="isLord" @click="setAdmin(member)">
                    {{ member.roleId === RoleEnum.ADMIN ? '撤销管理员' : '设为管理员' }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="muteMember(member)">设置禁言</el-dropdown-item>
                  <el-dropdown-item @click="unmuteMember(member)">解除禁言</el-dropdown-item>
                  <el-dropdown-item v-if="isLord" divided @click="transferOwner(member)">转让群主</el-dropdown-item>
                  <el-dropdown-item divided class="danger-item" @click="removeMember(member)">移出群聊</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </article>
        <div v-if="!filteredMembers.length" class="empty-members">
          <IEpUser />
          <p>没有找到匹配的群成员</p>
        </div>
      </div>

      <el-button
        v-if="!groupStore.userListOptions.isLast"
        class="load-more"
        :loading="groupStore.userListOptions.loading"
        @click="groupStore.loadMore"
      >加载更多成员</el-button>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.group-settings {
  min-height: 100%;
  padding: 0 24px 32px;
  color: #17233b;
  background: #f5f7fb;
}

.settings-header {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 25px 0 18px;
  background: linear-gradient(180deg, #f5f7fb 80%, rgba(245, 247, 251, 0));

  .eyebrow { margin: 0 0 4px; color: #68758a; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; }
  h2 { margin: 0; font-size: 24px; line-height: 1.3; }
  p:not(.eyebrow) { margin: 5px 0 0; color: #8a97aa; font-size: 12px; }
}

.close-button,
.more-button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: #738198;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e2e8f2;
  border-radius: 11px;
  transition: 0.2s ease;
  &:hover { color: #2f66e8; border-color: #bfd1f8; transform: translateY(-1px); }
}

.group-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px;
  background: linear-gradient(135deg, #fff, #f0f5ff);
  border: 1px solid #e1e9f7;
  border-radius: 18px;
  box-shadow: 0 12px 30px rgba(44, 78, 135, 0.08);
}

.group-copy {
  min-width: 0;
  flex: 1;
  h3 { margin: 0 0 5px; overflow: hidden; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
  p { margin: 0; color: #8491a6; font-size: 12px; }
}

.my-role,
.role-tag {
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  &.lord { color: #a85b00; background: #fff2d5; }
  &.admin { color: #315fcb; background: #e9f0ff; }
  &.member { color: #657289; background: #eef2f7; }
}

.setting-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 5px;
  margin: 18px 0;
  background: #eaf0f8;
  border-radius: 13px;
  button {
    padding: 10px;
    color: #758399;
    font-size: 13px;
    font-weight: 700;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 9px;
    span { margin-left: 4px; color: #9aa5b5; font-weight: 500; }
    &.active { color: #2e63dc; background: #fff; box-shadow: 0 4px 14px rgba(42, 67, 110, 0.08); }
  }
}

.tab-content { display: flex; flex-direction: column; gap: 12px; }

.setting-card {
  padding: 17px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 16px;
}

.card-heading {
  display: flex;
  gap: 11px;
  align-items: center;
  margin-bottom: 14px;
  h4 { margin: 0 0 3px; font-size: 14px; }
  p { margin: 0; color: #8b97a9; font-size: 11px; line-height: 1.45; }
}

.heading-icon {
  display: grid;
  flex: 0 0 34px;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  &.blue { color: #376ee6; background: #eaf1ff; }
  &.amber { color: #376ee6; background: #eaf1ff; }
  &.green { color: #376ee6; background: #eaf1ff; }
  &.red { color: #d8565d; background: #fff0f1; }
}

.field-row { display: flex; gap: 9px; }
.card-actions { display: flex; justify-content: flex-end; margin-top: 11px; }
.invite-card,
.danger-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; .card-heading { margin: 0; } }
.danger-card { border-color: #f3d9dc; }

.secondary-action {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 6px;
  align-items: center;
  justify-content: center;
  min-width: 112px;
  height: 38px;
  padding: 0 15px;
  color: #315fce;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  background: #edf3ff;
  border: 1px solid #cbdaf8;
  border-radius: 10px;
  transition: 0.2s ease;

  &:hover {
    color: #fff;
    background: #315fce;
    border-color: #315fce;
  }
}

.member-toolbar { display: flex; gap: 9px; }
.permission-note {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 10px 12px;
  color: #64748b;
  font-size: 11px;
  background: #edf4ff;
  border-radius: 10px;
}
.member-list { overflow: hidden; background: #fff; border: 1px solid #e5eaf2; border-radius: 16px; }
.member-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 70px;
  padding: 10px 13px;
  border-bottom: 1px solid #eef2f7;
  &:last-child { border-bottom: 0; }
}
.member-profile { display: flex; min-width: 0; gap: 11px; align-items: center; }
.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
  i { position: absolute; right: 1px; bottom: 1px; width: 10px; height: 10px; background: #b7c0cd; border: 2px solid #fff; border-radius: 50%; }
  i.online { background: #28c68b; }
}
.member-copy {
  min-width: 0;
  .member-name { display: flex; gap: 5px; align-items: center; }
  strong { max-width: 150px; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
  p { margin: 4px 0 0; overflow: hidden; color: #99a3b3; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
}
.me-tag { padding: 2px 5px; color: #63738c; font-size: 9px; background: #edf1f6; border-radius: 5px; }
.role-tag { padding: 2px 6px; font-size: 9px; }
.more-button { width: 32px; height: 32px; border-radius: 9px; }
.empty-members { padding: 50px 20px; color: #9aa6b7; text-align: center; svg { font-size: 28px; } p { margin: 8px 0 0; font-size: 12px; } }
.load-more { width: 100%; }

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  background: #f7f9fc;
  box-shadow: 0 0 0 1px #e1e7f0 inset;
  border-radius: 10px;
}

@media (max-width: 640px) {
  .group-settings { padding: 0 14px 24px; }
  .settings-header { padding-top: 18px; }
  .invite-card,
  .danger-card { align-items: flex-start; flex-direction: column; }
  .group-card { padding: 14px; }
  .group-copy p { line-height: 1.5; }
  .my-role { display: none; }
  .member-copy strong { max-width: 105px; }
}
</style>

<style lang="scss">
.danger-item { color: #e2555d !important; }
</style>
