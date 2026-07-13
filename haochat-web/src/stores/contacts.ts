import { reactive } from 'vue'
import { defineStore } from 'pinia'
import apis from '@/services/apis'
import { useGlobalStore } from '@/stores/global'
import type { ContactItem, RequestFriendItem } from '@/services/types'
import { RequestFriendAgreeStatus } from '@/services/types'

export const pageSize = 20
export const useContactStore = defineStore('contact', () => {
  const globalStore = useGlobalStore()
  const contactsList = reactive<ContactItem[]>([])
  const requestFriendsList = reactive<RequestFriendItem[]>([])
  const sentFriendsList = reactive<RequestFriendItem[]>([])
  const contactsOptions = reactive({ isLast: false, isLoading: false, cursor: '' })
  const requestFriendsOptions = reactive({ isLast: false, isLoading: false, pageNo: 1 })
  const sentFriendsOptions = reactive({ isLast: false, isLoading: false, pageNo: 1 })
  const getContactList = async (isFresh = false) => {
    if (!isFresh) {
      if (contactsOptions.isLast || contactsOptions.isLoading) return
    }
    contactsOptions.isLoading = true
    const data = await apis
      .getContactList({
        // TODO 先写 100，稍后优化
        pageSize: 100,
        cursor: isFresh || !contactsOptions.cursor ? undefined : contactsOptions.cursor,
      })
      .send()
      .catch(() => {
        contactsOptions.isLoading = false
      })
    if (!data) return
    isFresh
      ? contactsList.splice(0, contactsList.length, ...data.list)
      : contactsList.push(...data.list)
    contactsOptions.cursor = data.cursor
    contactsOptions.isLast = data.isLast
    contactsOptions.isLoading = false
  }

  /** 好友申请未读数 */
  const getNewFriendCount = async () => {
    const data = await apis
      .newFriendCount()
      .send()
      .catch(() => {
        //
      })
    if (typeof data?.unReadCount === 'number') {
      globalStore.unReadMark.newFriendUnreadCount = data.unReadCount
    }
  }

  const getRequestFriendsList = async (isFresh = false) => {
    if (!isFresh) {
      if (requestFriendsOptions.isLast || requestFriendsOptions.isLoading) return
    }
    requestFriendsOptions.isLoading = true
    const pageNo = isFresh ? 1 : requestFriendsOptions.pageNo
    const data = await apis
      .requestFriendList({
        pageSize,
        pageNo,
      })
      .send()
      .catch(() => {
        requestFriendsOptions.isLoading = false
      })
    // 每次加载完新的好友邀请列表都要更新申请未读数
    getNewFriendCount()
    if (!data) return
    isFresh
      ? requestFriendsList.splice(0, requestFriendsList.length, ...data.list)
      : requestFriendsList.push(...data.list)
    requestFriendsOptions.isLast = data.isLast
    requestFriendsOptions.pageNo = data.isLast ? pageNo : pageNo + 1
    requestFriendsOptions.isLoading = false
  }

  const getSentFriendsList = async (isFresh = false) => {
    if (!isFresh) {
      if (sentFriendsOptions.isLast || sentFriendsOptions.isLoading) return
    }
    sentFriendsOptions.isLoading = true
    const pageNo = isFresh ? 1 : sentFriendsOptions.pageNo
    const data = await apis
      .sentFriendList({
        pageSize,
        pageNo,
      })
      .send()
      .catch(() => {
        sentFriendsOptions.isLoading = false
      })
    if (!data) return
    isFresh
      ? sentFriendsList.splice(0, sentFriendsList.length, ...data.list)
      : sentFriendsList.push(...data.list)
    sentFriendsOptions.isLast = data.isLast
    sentFriendsOptions.pageNo = data.isLast ? pageNo : pageNo + 1
    sentFriendsOptions.isLoading = false
  }
  // 默认执行一次
  // getContactList()
  // getRequestFriendsList()
  /** 接受好友请求 */
  const onAcceptFriend = async (applyId: number) => {
    await apis.applyFriendRequest({ applyId }).send()
    // 刷新好友申请列表
    await getRequestFriendsList(true)
    // 刷新好友列表
    await getContactList(true)
    // 标识为可以发消息的人
    if (globalStore.currentSelectedContact) {
      // @ts-ignore
      globalStore.currentSelectedContact.status = RequestFriendAgreeStatus.Agree
    }
  }

  const onRejectFriend = async (applyId: number) => {
    await apis.rejectFriendRequest({ applyId }).send()
    await getRequestFriendsList(true)
  }
  /** 删除好友 */
  const onDeleteContact = async (uid: number) => {
    if (!uid) return
    await apis.deleteFriend({ targetUid: uid }).send()
    // 刷新好友申请列表
    // getRequestFriendsList(true)
    // 刷新好友列表
    await getContactList(true)
  }
  return {
    getContactList,
    getRequestFriendsList,
    contactsList,
    requestFriendsList,
    sentFriendsList,
    contactsOptions,
    requestFriendsOptions,
    sentFriendsOptions,
    onAcceptFriend,
    onRejectFriend,
    onDeleteContact,
    getSentFriendsList,
  }
})
