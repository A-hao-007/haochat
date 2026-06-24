<script setup lang="ts">
import { ref, onBeforeMount, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useContactStore } from '@/stores/contacts'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import { useUserInfo } from '@/hooks/useCached'
import { useChatStore } from '@/stores/chat'
import apis from '@/services/apis'
import { OnlineEnum, RoomTypeEnum } from '@/enums'
import { ElMessage } from 'element-plus'
import type { RequestFriendItem } from '@/services/types'
import { RequestFriendAgreeStatus } from '@/services/types'

const router = useRouter()
const contactStore = useContactStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const chatStore = useChatStore()

const activeTab = ref<'contacts' | 'requests'>('contacts')
const searchKeyword = ref('')
const searchResults = ref<{ uid: number; name: string; avatar: string }[]>([])
const searching = ref(false)
const addingUids = ref(new Set<number>())

// 联系人列表
const contactsList = computed(() => contactStore.contactsList || [])
const requestsList = computed(() => contactStore.requestFriendsList || [])

const newRequestCount = computed(() =>
  requestsList.value.filter((r: RequestFriendItem) => r.status === RequestFriendAgreeStatus.Waiting).length
)

onBeforeMount(async () => {
  await contactStore.getContactList(true)
  await contactStore.getRequestFriendsList(true)
  // 批量预加载联系人用户信息
  const uids = contactStore.contactsList.map((c: { uid: number }) => c.uid)
  if (uids.length) cachedStore.getBatchUserInfo(uids)
})

/** 获取联系人信息 */
const getContactInfo = (uid: number) => {
  const info = useUserInfo(uid)
  return {
    name: info.value?.name || '用户' + uid,
    avatar: info.value?.avatar || '',
    online: contactStore.contactsList.find((c: { uid: number }) => c.uid === uid)?.activeStatus === OnlineEnum.ONLINE,
  }
}

/** 搜索用户 */
const doSearch = async () => {
  const kw = searchKeyword.value.trim()
  if (!kw || kw.length < 1) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const res = await apis.searchUser(kw).send()
    searchResults.value = res || []
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

// 防抖搜索
let searchTimer: ReturnType<typeof setTimeout>
watch(searchKeyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
})

/** 添加好友 */
const addFriend = async (uid: number) => {
  if (addingUids.value.has(uid)) return
  addingUids.value.add(uid)
  try {
    await apis.sendAddFriendRequest({ targetUid: uid, msg: '你好，我想加你为好友' }).send()
    ElMessage.success('好友请求已发送')
    searchResults.value = searchResults.value.filter(u => u.uid !== uid)
  } catch (err: any) {
    ElMessage.error(err?.message || '发送失败')
  } finally {
    addingUids.value.delete(uid)
  }
}

/** 接受好友请求 */
const acceptRequest = async (applyId: number) => {
  try {
    await apis.applyFriendRequest({ applyId }).send()
    ElMessage.success('已添加好友')
    contactStore.onAcceptFriend(applyId)
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

/** 发消息 */
const sendMessage = async (uid: number) => {
  try {
    const result = await apis.sessionDetailWithFriends({ uid }).send()
    globalStore.currentSession.roomId = result.roomId
    globalStore.currentSession.type = RoomTypeEnum.Single
    chatStore.updateSessionLastActiveTime(result.roomId, result)
    router.push('/')
  } catch {
    ElMessage.error('操作失败')
  }
}

/** 创建群聊 */
const showCreateGroup = ref(false)
const selectedUids = ref(new Set<number>())

const toggleSelectUid = (uid: number) => {
  const s = new Set(selectedUids.value)
  if (s.has(uid)) s.delete(uid); else s.add(uid)
  selectedUids.value = s
}

const createGroup = async () => {
  const ids = [...selectedUids.value]
  if (ids.length < 1) { ElMessage.warning('请至少选择一个联系人'); return }
  try {
    await apis.createGroup({ uidList: ids }).send()
    ElMessage.success('群聊创建成功')
    showCreateGroup.value = false
    selectedUids.value = new Set()
  } catch (err: any) { ElMessage.error(err?.message || '创建失败') }
}
const deleteFriend = async (uid: number) => {
  try {
    await apis.deleteFriend({ targetUid: uid }).send()
    ElMessage.success('已删除')
    contactStore.onDeleteContact(uid)
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}
</script>

<template>
  <div class="contacts-page">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <IEpSearch class="search-icon" :size="16" />
      <input
        v-model="searchKeyword"
        class="search-input"
        placeholder="搜索用户名或昵称添加好友..."
        @keyup.enter="doSearch"
      />
      <IEpClose
        v-if="searchKeyword"
        class="search-clear"
        :size="14"
        @click="searchKeyword = ''; searchResults = []"
      />
    </div>

    <!-- 创建群聊按钮 -->
    <div class="action-bar">
      <button class="create-group-btn" @click="showCreateGroup = !showCreateGroup">
        <IEpPlus :size="14" /> 创建群聊
      </button>
    </div>
    <div v-if="showCreateGroup" class="create-group-panel">
      <div class="cg-title">选择群成员（点击联系人）</div>
      <div class="cg-list">
        <div v-if="contactsList.length === 0" class="empty">暂无联系人</div>
        <div
          v-for="item in contactsList"
          :key="item.uid"
          :class="['cg-item', { selected: selectedUids.has(item.uid) }]"
          @click="toggleSelectUid(item.uid)"
        >
          <el-avatar :size="36" :src="getContactInfo(item.uid).avatar" />
          <span class="cg-name">{{ getContactInfo(item.uid).name }}</span>
          <span v-if="selectedUids.has(item.uid)" class="cg-check">✓</span>
        </div>
      </div>
      <button class="submit-btn" @click="createGroup" :disabled="selectedUids.size === 0">
        创建群聊 (已选{{ selectedUids.size }}人)
      </button>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <div class="results-header">搜索结果 ({{ searchResults.length }})</div>
      <div
        v-for="user in searchResults"
        :key="user.uid"
        class="result-item"
      >
        <el-avatar :size="40" :src="user.avatar" />
        <div class="result-info">
          <span class="result-name">{{ user.name }}</span>
          <span class="result-uid">UID: {{ user.uid }}</span>
        </div>
        <button class="add-btn" @click="addFriend(user.uid)" :disabled="addingUids.has(user.uid)">
          {{ addingUids.has(user.uid) ? '...' : '添加' }}
        </button>
      </div>
    </div>

    <!-- Tab 切换 -->
    <div class="tabs">
      <button
        :class="['tab', { active: activeTab === 'contacts' }]"
        @click="activeTab = 'contacts'"
      >
        联系人 ({{ contactsList.length }})
      </button>
      <button
        :class="['tab', { active: activeTab === 'requests' }]"
        @click="activeTab = 'requests'"
      >
        新的朋友
        <span v-if="newRequestCount" class="badge">{{ newRequestCount }}</span>
      </button>
    </div>

    <!-- 联系人列表 -->
    <div v-if="activeTab === 'contacts'" class="list-section">
      <div v-if="contactsList.length === 0" class="empty">暂无联系人，搜索用户名添加好友吧</div>
      <div
        v-for="item in contactsList"
        :key="item.uid"
        class="contact-item"
      >
        <el-avatar :size="44" :src="getContactInfo(item.uid).avatar" />
        <div class="contact-info">
          <span class="contact-name">{{ getContactInfo(item.uid).name }}</span>
          <span class="contact-status" :class="{ online: getContactInfo(item.uid).online }">
            {{ getContactInfo(item.uid).online ? '在线' : '离线' }}
          </span>
        </div>
        <div class="contact-actions">
          <button class="msg-btn" @click="sendMessage(item.uid)">发消息</button>
          <button class="del-btn" @click="deleteFriend(item.uid)">删除</button>
        </div>
      </div>
    </div>

    <!-- 好友请求列表 -->
    <div v-if="activeTab === 'requests'" class="list-section">
      <div v-if="requestsList.length === 0" class="empty">暂无好友请求</div>
      <div
        v-for="req in requestsList"
        :key="req.applyId"
        class="request-item"
      >
        <el-avatar :size="44" :src="cachedStore.userCachedList[req.uid]?.avatar" />
        <div class="request-info">
          <span class="request-name">{{ cachedStore.userCachedList[req.uid]?.name || '用户' + req.uid }}</span>
          <span class="request-msg">{{ req.msg || '请求添加你为好友' }}</span>
        </div>
        <div class="request-actions">
          <button
            v-if="req.status === RequestFriendAgreeStatus.Waiting"
            class="accept-btn"
            @click="acceptRequest(req.applyId)"
          >
            接受
          </button>
          <span v-else class="accepted-text">已添加</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
