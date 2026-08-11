<script setup lang="ts">
import { computed, onBeforeMount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import apis from '@/services/apis'
import { RoomTypeEnum } from '@/enums'
import { useContactStore } from '@/stores/contacts'
import { useCachedStore } from '@/stores/cached'
import { useGlobalStore } from '@/stores/global'
import { useGroupStore } from '@/stores/group'
import { useUserInfo } from '@/hooks/useCached'

const router = useRouter()
const contactStore = useContactStore()
const cachedStore = useCachedStore()
const globalStore = useGlobalStore()
const groupStore = useGroupStore()

const groupKeyword = ref('')
const showCreateGroup = ref(false)
const selectedUids = ref(new Set<number>())
const groupName = ref('')

const contactsList = computed(() => contactStore.contactsList || [])
const filteredGroups = computed(() => {
  const keyword = groupKeyword.value.trim().toLocaleLowerCase()
  if (!keyword) return groupStore.myGroupList
  return groupStore.myGroupList.filter((group) =>
    `${group.name || ''} ${group.text || ''}`.toLocaleLowerCase().includes(keyword),
  )
})
const myCreatedGroups = computed(() => filteredGroups.value.filter((group) => group.isLord))
const myJoinedGroups = computed(() => filteredGroups.value.filter((group) => !group.isLord))

onBeforeMount(async () => {
  await Promise.all([groupStore.getMyGroupList(), contactStore.getContactList(true)])
  const uids = contactStore.contactsList.map((contact: { uid: number }) => contact.uid)
  if (uids.length) cachedStore.getBatchUserInfo(uids)
})

const getContactInfo = (uid: number) => {
  const info = useUserInfo(uid)
  return {
    name: info.value?.name || `用户${uid}`,
    avatar: info.value?.avatar || '',
  }
}

const openGroup = (roomId: number) => {
  globalStore.currentSession.roomId = roomId
  globalStore.currentSession.type = RoomTypeEnum.Group
  router.push('/')
}

const toggleCreatePanel = () => {
  showCreateGroup.value = !showCreateGroup.value
}

const closeCreatePanel = () => {
  showCreateGroup.value = false
  selectedUids.value = new Set()
  groupName.value = ''
}

const toggleSelectUid = (uid: number) => {
  const selected = new Set(selectedUids.value)
  if (selected.has(uid)) selected.delete(uid)
  else selected.add(uid)
  selectedUids.value = selected
}

const createGroup = async () => {
  const uidList = [...selectedUids.value]
  if (!uidList.length) {
    ElMessage.warning('请至少选择一位联系人')
    return
  }
  try {
    await apis.createGroup({ uidList, name: groupName.value.trim() || undefined }).send()
    ElMessage.success('群聊创建成功')
    closeCreatePanel()
    await groupStore.getMyGroupList()
  } catch (err: any) {
    ElMessage.error(err?.message || '创建失败')
  }
}
</script>

<template>
  <div class="groups-page">
    <header class="groups-heading">
      <div>
        <span class="eyebrow">群组中心</span>
        <h1>我的群组</h1>
        <p>集中管理你创建和加入的群聊</p>
      </div>
      <button class="primary-action" type="button" @click="toggleCreatePanel">
        <IEpPlus :size="16" />
        {{ showCreateGroup ? '收起创建面板' : '创建群聊' }}
      </button>
    </header>

    <section v-if="showCreateGroup" class="create-panel">
      <div class="create-copy">
        <h2>创建新群聊</h2>
        <p>设置群名称，并从联系人中选择首批成员。</p>
      </div>
      <div class="create-form">
        <label>
          <span>群名称</span>
          <input v-model="groupName" maxlength="30" placeholder="例如：周末活动小组" />
        </label>
        <div class="member-field">
          <span class="field-label">选择成员</span>
          <div v-if="contactsList.length" class="member-grid">
            <button
              v-for="contact in contactsList"
              :key="contact.uid"
              :class="['member-option', { selected: selectedUids.has(contact.uid) }]"
              type="button"
              @click="toggleSelectUid(contact.uid)"
            >
              <el-avatar :size="34" :src="getContactInfo(contact.uid).avatar" />
              <span>{{ getContactInfo(contact.uid).name }}</span>
              <IEpCheck v-if="selectedUids.has(contact.uid)" :size="15" />
            </button>
          </div>
          <div v-else class="no-contacts">还没有联系人，请先到“联系人”页面添加好友。</div>
        </div>
        <div class="create-actions">
          <button class="secondary-action" type="button" @click="closeCreatePanel">取消</button>
          <button
            class="submit-action"
            type="button"
            :disabled="selectedUids.size === 0"
            @click="createGroup"
          >
            创建群聊<span v-if="selectedUids.size">（{{ selectedUids.size }}人）</span>
          </button>
        </div>
      </div>
    </section>

    <section class="group-toolbar">
      <div class="group-search">
        <IEpSearch :size="17" />
        <input v-model="groupKeyword" placeholder="搜索群名称或最近消息" />
        <button v-if="groupKeyword" type="button" aria-label="清除搜索" @click="groupKeyword = ''">
          <IEpClose :size="14" />
        </button>
      </div>
      <div class="group-stat">
        <strong>{{ groupStore.myGroupList.length }}</strong>
        <span>个群聊</span>
      </div>
    </section>

    <main class="groups-content">
      <div v-if="groupStore.myGroupListLoading" class="state-card">正在加载群组…</div>
      <div v-else-if="!groupStore.myGroupList.length" class="empty-groups">
        <div class="empty-icon"><Icon icon="group" :size="30" /></div>
        <h2>还没有加入群聊</h2>
        <p>邀请联系人创建一个群聊，之后所有群组都会在这里集中显示。</p>
        <button type="button" @click="showCreateGroup = true"><IEpPlus :size="15" /> 创建第一个群聊</button>
      </div>
      <div v-else-if="!filteredGroups.length" class="state-card">
        没有找到与“{{ groupKeyword }}”匹配的群组
      </div>
      <template v-else>
        <section v-if="myCreatedGroups.length" class="group-section">
          <div class="section-title">
            <div><h2>我创建的</h2><span>{{ myCreatedGroups.length }}</span></div>
            <p>你是这些群聊的群主</p>
          </div>
          <div class="group-grid">
            <button
              v-for="group in myCreatedGroups"
              :key="group.roomId"
              class="group-card"
              type="button"
              @click="openGroup(group.roomId)"
            >
              <el-avatar :size="48" :src="group.avatar" />
              <span class="group-copy">
                <strong>{{ group.name || `群聊 ${group.roomId}` }}</strong>
                <small>{{ group.text || '暂时没有新消息' }}</small>
              </span>
              <span v-if="group.unreadCount" class="unread">{{ Math.min(group.unreadCount, 99) }}</span>
              <IEpArrowRight :size="16" class="arrow" />
            </button>
          </div>
        </section>

        <section v-if="myJoinedGroups.length" class="group-section">
          <div class="section-title">
            <div><h2>我加入的</h2><span>{{ myJoinedGroups.length }}</span></div>
            <p>与你相关的其他群聊</p>
          </div>
          <div class="group-grid">
            <button
              v-for="group in myJoinedGroups"
              :key="group.roomId"
              class="group-card"
              type="button"
              @click="openGroup(group.roomId)"
            >
              <el-avatar :size="48" :src="group.avatar" />
              <span class="group-copy">
                <strong>{{ group.name || `群聊 ${group.roomId}` }}</strong>
                <small>{{ group.text || '暂时没有新消息' }}</small>
              </span>
              <span v-if="group.unreadCount" class="unread">{{ Math.min(group.unreadCount, 99) }}</span>
              <IEpArrowRight :size="16" class="arrow" />
            </button>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
