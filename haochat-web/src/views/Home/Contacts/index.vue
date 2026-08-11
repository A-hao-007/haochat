<script setup lang="ts">
import { ref, onBeforeMount, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
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
const route = useRoute()
const contactStore = useContactStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const chatStore = useChatStore()
const activeTab = ref<'contacts' | 'requests' | 'sent'>('contacts')
const searchKeyword = ref('')
const searchResults = ref<{ uid: number; name: string; avatar: string }[]>([])
const searching = ref(false)
const addingUids = ref(new Set<number>())
const searchInput = ref<HTMLInputElement>()

// 联系人列表
const contactsList = computed(() => contactStore.contactsList || [])
const requestsList = computed(() => contactStore.requestFriendsList || [])
const sentRequestsList = computed(() => contactStore.sentFriendsList || [])

const newRequestCount = computed(
  () =>
    requestsList.value.filter(
      (r: RequestFriendItem) => r.status === RequestFriendAgreeStatus.Waiting,
    ).length,
)

onBeforeMount(async () => {
  await contactStore.getContactList(true)
  await contactStore.getRequestFriendsList(true)
  await contactStore.getSentFriendsList(true)
  // 批量预加载联系人用户信息
  const uids = [
    ...contactStore.contactsList.map((c: { uid: number }) => c.uid),
    ...contactStore.requestFriendsList.map((r: { uid: number }) => r.uid),
    ...contactStore.sentFriendsList.map((r: { uid: number }) => r.uid),
  ]
  if (uids.length) cachedStore.getBatchUserInfo(uids)
})

/** 获取联系人信息 */
const getContactInfo = (uid: number) => {
  const info = useUserInfo(uid)
  return {
    name: info.value?.name || '用户' + uid,
    avatar: info.value?.avatar || '',
    online:
      contactStore.contactsList.find((c: { uid: number }) => c.uid === uid)?.activeStatus ===
      OnlineEnum.ONLINE,
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

const clearSearch = () => {
  searchKeyword.value = ''
  searchResults.value = []
}

const focusSearch = () => {
  activeTab.value = 'contacts'
  nextTick(() => searchInput.value?.focus())
}

const handleSearch = () => {
  if (!searchKeyword.value.trim()) {
    focusSearch()
    ElMessage.info('请输入用户名或昵称后再查找')
    return
  }
  doSearch()
}

const switchTab = (tab: 'contacts' | 'requests' | 'sent') => {
  activeTab.value = tab
  if (tab !== 'contacts') clearSearch()
}

watch(
  () => route.query.add,
  (add) => {
    if (add === '1') {
      focusSearch()
      router.replace({ path: '/contact' })
    }
  },
  { immediate: true },
)

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
    await contactStore.getSentFriendsList(true)
    searchResults.value = searchResults.value.filter((u) => u.uid !== uid)
  } catch (err: any) {
    ElMessage.error(err?.message || '发送失败')
  } finally {
    addingUids.value.delete(uid)
  }
}

/** 接受好友请求 */
const acceptRequest = async (applyId: number) => {
  try {
    await contactStore.onAcceptFriend(applyId)
    ElMessage.success('已添加好友')
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

/** 拒绝好友请求 */
const rejectRequest = async (applyId: number) => {
  try {
    await contactStore.onRejectFriend(applyId)
    ElMessage.success('已拒绝')
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

const requestStatusText = (status: RequestFriendAgreeStatus) => {
  if (status === RequestFriendAgreeStatus.Agree) return '已添加'
  if (status === RequestFriendAgreeStatus.Reject) return '已拒绝'
  return '待验证'
}
const deleteFriend = async (uid: number) => {
  try {
    await contactStore.onDeleteContact(uid)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}
</script>

<template>
  <div class="contacts-page">
    <div class="contacts-shell">
      <header class="page-heading">
        <div>
          <span class="eyebrow">好友中心</span>
          <h1>联系人</h1>
          <p>找到熟悉的人，建立联系并开始聊天</p>
        </div>
        <div class="contact-summary">
          <div class="summary-item">
            <strong>{{ contactsList.length }}</strong>
            <span>好友</span>
          </div>
          <button
            :class="['summary-item', 'request-summary', { attention: newRequestCount }]"
            type="button"
            @click="switchTab('requests')"
          >
            <strong>{{ newRequestCount }}</strong>
            <span>待处理申请</span>
          </button>
        </div>
      </header>

      <section class="discovery-card">
        <div class="discovery-copy">
          <span class="discovery-icon"><IEpPlus :size="18" /></span>
          <div><strong>添加新好友</strong><small>可通过用户名或昵称查找</small></div>
        </div>
        <div class="search-bar">
          <IEpSearch class="search-icon" :size="17" />
          <input
            ref="searchInput"
            v-model="searchKeyword"
            class="search-input"
            placeholder="输入用户名或昵称"
            @keyup.enter="handleSearch"
          />
          <span v-if="searching" class="searching">搜索中…</span>
          <button v-else-if="searchKeyword" class="search-clear" type="button" @click="clearSearch">
            <IEpClose :size="14" />
          </button>
          <button class="search-button" type="button" @click="handleSearch">
            查找好友
          </button>
        </div>
      </section>

      <section v-if="searchKeyword.trim()" class="search-results">
        <div class="results-header">
          <div><strong>搜索结果</strong><span v-if="searchResults.length">{{ searchResults.length }} 人</span></div>
          <button type="button" @click="clearSearch">返回联系人</button>
        </div>
        <div v-if="!searching && searchResults.length === 0" class="search-empty">
          未找到匹配用户，请检查用户名或昵称
        </div>
        <div class="result-grid">
          <div v-for="user in searchResults" :key="user.uid" class="result-item">
            <el-avatar :size="44" :src="user.avatar" />
            <div class="result-info">
              <span class="result-name">{{ user.name }}</span>
              <span class="result-uid">HaoChat ID {{ user.uid }}</span>
            </div>
            <button class="add-btn" type="button" :disabled="addingUids.has(user.uid)" @click="addFriend(user.uid)">
              {{ addingUids.has(user.uid) ? '发送中' : '添加好友' }}
            </button>
          </div>
        </div>
      </section>

      <section v-else class="contacts-panel">
        <div class="tabs" role="tablist">
          <button :class="['tab', { active: activeTab === 'contacts' }]" type="button" @click="switchTab('contacts')">
            <IEpUser :size="16" />联系人<span>{{ contactsList.length }}</span>
          </button>
          <button :class="['tab', { active: activeTab === 'requests' }]" type="button" @click="switchTab('requests')">
            <IEpBell :size="16" />收到申请<span :class="{ badge: newRequestCount }">{{ newRequestCount }}</span>
          </button>
          <button :class="['tab', { active: activeTab === 'sent' }]" type="button" @click="switchTab('sent')">
            <IEpPromotion :size="16" />发出申请<span>{{ sentRequestsList.length }}</span>
          </button>
        </div>

        <div v-if="activeTab === 'contacts'" class="list-section contact-list">
          <div v-if="contactsList.length === 0" class="empty-state">
            <div class="empty-illustration"><IEpUserFilled :size="30" /><span>+</span></div>
            <h2>建立你的联系人列表</h2>
            <p>搜索用户名或昵称添加好友，好友通过后就可以随时发起私聊。</p>
            <button type="button" @click="focusSearch"><IEpSearch :size="15" /> 搜索好友</button>
          </div>
          <div v-else class="contact-grid">
            <article v-for="item in contactsList" :key="item.uid" class="contact-item">
              <div class="avatar-box">
                <el-avatar :size="48" :src="getContactInfo(item.uid).avatar" />
                <i :class="{ online: getContactInfo(item.uid).online }"></i>
              </div>
              <div class="contact-info">
                <span class="contact-name">{{ getContactInfo(item.uid).name }}</span>
                <span class="contact-status">{{ getContactInfo(item.uid).online ? '当前在线' : '暂时离线' }}</span>
              </div>
              <div class="contact-actions">
                <button class="msg-btn" type="button" @click="sendMessage(item.uid)"><IEpChatDotRound :size="14" /> 发消息</button>
                <el-popconfirm title="确定删除这位好友吗？" confirm-button-text="删除" cancel-button-text="取消" @confirm="deleteFriend(item.uid)">
                  <template #reference><button class="del-btn" type="button" aria-label="删除好友"><IEpDelete :size="15" /></button></template>
                </el-popconfirm>
              </div>
            </article>
          </div>
        </div>

        <div v-if="activeTab === 'requests'" class="list-section">
          <div v-if="requestsList.length === 0" class="empty-state compact">
            <div class="empty-illustration"><IEpBell :size="28" /></div><h2>暂无好友申请</h2><p>新的好友请求会显示在这里。</p>
          </div>
          <div v-else class="request-list">
            <div v-for="req in requestsList" :key="req.applyId" class="request-item">
              <el-avatar :size="46" :src="cachedStore.userCachedList[req.uid]?.avatar" />
              <div class="request-info"><span class="request-name">{{ cachedStore.userCachedList[req.uid]?.name || '用户' + req.uid }}</span><span class="request-msg">{{ req.msg || '请求添加你为好友' }}</span></div>
              <div class="request-actions">
                <template v-if="req.status === RequestFriendAgreeStatus.Waiting"><button class="accept-btn" type="button" @click="acceptRequest(req.applyId)">接受</button><button class="reject-btn" type="button" @click="rejectRequest(req.applyId)">拒绝</button></template>
                <span v-else class="accepted-text">{{ requestStatusText(req.status) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'sent'" class="list-section">
          <div v-if="sentRequestsList.length === 0" class="empty-state compact">
            <div class="empty-illustration"><IEpPromotion :size="28" /></div><h2>暂无发出的申请</h2><p>你主动添加的好友申请会显示在这里。</p>
            <button type="button" @click="focusSearch"><IEpPlus :size="15" /> 添加好友</button>
          </div>
          <div v-else class="request-list">
            <div v-for="req in sentRequestsList" :key="req.applyId" class="request-item">
              <el-avatar :size="46" :src="cachedStore.userCachedList[req.uid]?.avatar" />
              <div class="request-info"><span class="request-name">{{ cachedStore.userCachedList[req.uid]?.name || '用户' + req.uid }}</span><span class="request-msg">{{ req.msg || '好友申请' }}</span></div>
              <span class="accepted-text">{{ requestStatusText(req.status) }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
