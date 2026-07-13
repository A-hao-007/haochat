import { computed, reactive, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import cloneDeep from 'lodash/cloneDeep'
import { useRoute } from 'vue-router'
import apis from '@/services/apis'
import type { MarkItemType, MessageType, RevokedMsgType, SessionItem } from '@/services/types'
import { MarkEnum, MsgEnum, RoomTypeEnum } from '@/enums'
import { computedTimeBlock } from '@/utils/computedTime'
import { useCachedStore } from '@/stores/cached'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useGroupStore } from '@/stores/group'
import { useContactStore } from '@/stores/contacts'
import shakeTitle from '@/utils/shakeTitle'
import notify from '@/utils/notification'

export const pageSize = 20
// 标识是否第一次请求
let isFirstInit = false
// 标识AI助手清单是否已拉取过（登录后拉一次即可，不依赖是否已有会话）
let aiBotsFetched = false

export const useChatStore = defineStore('chat', () => {
  const route = useRoute()
  const cachedStore = useCachedStore()
  const userStore = useUserStore()
  const globalStore = useGlobalStore()
  const groupStore = useGroupStore()
  const contactStore = useContactStore()
  const sessionList = reactive<SessionItem[]>([]) // 会话列表
  const sessionOptions = reactive({ isLast: false, isLoading: false, cursor: '' })

  const currentRoomId = computed(() => globalStore.currentSession?.roomId)
  const currentRoomType = computed(() => globalStore.currentSession?.type)

  const messageMap = reactive<Map<number, Map<number, MessageType>>>(
    new Map([[currentRoomId.value, new Map()]]),
  ) // 消息Map
  const messageOptions = reactive<
    Map<number, { isLast: boolean; isLoading: boolean; cursor: string }>
  >(new Map([[currentRoomId.value, { isLast: false, isLoading: false, cursor: '' }]]))
  const replyMapping = reactive<Map<number, Map<number, number[]>>>(
    new Map([[currentRoomId.value, new Map()]]),
  ) // 回复消息映射
  // AI流式回复占位消息：roomId -> 临时消息id（负数）。同一房间同一时刻只会有一次进行中的流式回复
  const streamingPlaceholder = reactive<Map<number, number>>(new Map())

  // 未读分割线：roomId -> 进入会话那一刻"第一条未读消息"的id，渲染时在这条消息上方画"以下是新消息"。
  // 用消息id而不是下标定位——往上翻页/新消息到达都会改变下标，id是稳定的
  const firstUnreadMsgId = reactive<Map<number, number>>(new Map())
  // 进入会话时消息可能还没加载完，先记未读数，等 getMsgList 回来后再解析成消息id
  const pendingUnreadCount = new Map<number, number>()

  const resolveFirstUnread = (roomId: number) => {
    const count = pendingUnreadCount.get(roomId)
    if (!count) return
    const msgs = [...(messageMap.get(roomId)?.values() || [])]
    if (msgs.length === 0) return // 消息还没加载，留到 getMsgList 完成后再解析
    pendingUnreadCount.delete(roomId)
    // 未读的就是最后 count 条；未读数超过已加载条数时退化为"已加载的第一条"
    const target = msgs[Math.max(msgs.length - count, 0)]
    if (target) firstUnreadMsgId.set(roomId, target.message.id)
  }

  const currentMessageMap = computed({
    get: () => {
      const current = messageMap.get(currentRoomId.value as number)
      if (current === undefined) {
        messageMap.set(currentRoomId.value, new Map())
      }
      return messageMap.get(currentRoomId.value as number)
    },
    set: (val) => {
      messageMap.set(currentRoomId.value, val as Map<number, MessageType>)
    },
  })
  const currentMessageOptions = computed({
    get: () => {
      const current = messageOptions.get(currentRoomId.value as number)
      if (current === undefined) {
        messageOptions.set(currentRoomId.value, { isLast: false, isLoading: true, cursor: '' })
      }
      return messageOptions.get(currentRoomId.value as number)
    },
    set: (val) => {
      messageOptions.set(
        currentRoomId.value,
        val as { isLast: boolean; isLoading: boolean; cursor: string },
      )
    },
  })
  const currentReplyMap = computed({
    get: () => {
      const current = replyMapping.get(currentRoomId.value as number)
      if (current === undefined) {
        replyMapping.set(currentRoomId.value, new Map())
      }
      return replyMapping.get(currentRoomId.value as number)
    },
    set: (val) => {
      replyMapping.set(currentRoomId.value, val as Map<number, number[]>)
    },
  })
  const isGroup = computed(() => currentRoomType.value === RoomTypeEnum.Group)
  /**
   * 获取当前会话信息
   */
  const currentSessionInfo = computed(() =>
    sessionList.find((session) => session.roomId === globalStore.currentSession?.roomId),
  )

  const chatListToBottomAction = ref<() => void>() // 外部提供消息列表滚动到底部事件

  // const isStartCount = ref(false) // 是否开始计数
  // const newMsgCount = ref(0) // 新消息计数

  const newMsgCount = reactive<Map<number, { count: number; isStart: boolean }>>(
    new Map([
      [
        currentRoomId.value,
        {
          // 新消息计数
          count: 0,
          // 是否开始计数
          isStart: false,
        },
      ],
    ]),
  )
  const currentNewMsgCount = computed({
    get: () => {
      const current = newMsgCount.get(currentRoomId.value as number)
      if (current === undefined) {
        newMsgCount.set(currentRoomId.value, { count: 0, isStart: false })
      }
      return newMsgCount.get(currentRoomId.value as number)
    },
    set: (val) => {
      newMsgCount.set(currentRoomId.value, val as { count: number; isStart: boolean })
    },
  })

  watch(currentRoomId, (val, oldVal) => {
    if (oldVal !== undefined && val !== oldVal) {
      // 切换会话，滚动到底部
      chatListToBottomAction.value?.()
      // 切换的 rooms是空数据的话就请求消息列表
      if (!currentMessageMap.value || currentMessageMap.value.size === 0) {
        if (!currentMessageMap.value) {
          messageMap.set(currentRoomId.value as number, new Map())
        }
        getMsgList()
      }

      // 群组的时候去请求群成员/群详情
      if (currentRoomType.value === RoomTypeEnum.Group) {
        groupStore.getGroupUserList(true)
        groupStore.getCountStatistic()
      }
      // @候选人列表：群聊是真实成员+AI助手，单聊是对方（好友或AI助手），两种场景都要拉取
      cachedStore.getGroupAtUserBaseInfo()
    }

    // 重置当前回复的消息
    currentMsgReply.value = {}
  })

  // 当前消息回复
  const currentMsgReply = ref<Partial<MessageType>>({})

  // 将消息列表转换为数组
  const chatMessageList = computed(() => [...(currentMessageMap.value?.values() || [])])

  const getMsgList = async (size = pageSize) => {
    // 捕获本次请求目标的 roomId，避免请求期间用户切换会话导致响应数据写错房间（消息串台）
    const targetRoomId = currentRoomId.value as number
    if (targetRoomId === undefined) return
    if (!messageMap.has(targetRoomId)) messageMap.set(targetRoomId, new Map())
    if (!messageOptions.has(targetRoomId))
      messageOptions.set(targetRoomId, { isLast: false, isLoading: false, cursor: '' })
    if (!replyMapping.has(targetRoomId)) replyMapping.set(targetRoomId, new Map())
    const targetMessageMap = messageMap.get(targetRoomId) as Map<number, MessageType>
    const targetOptions = messageOptions.get(targetRoomId) as {
      isLast: boolean
      isLoading: boolean
      cursor: string
    }
    const targetReplyMap = replyMapping.get(targetRoomId) as Map<number, number[]>

    targetOptions.isLoading = true
    const data = await apis
      .getMsgList({
        params: {
          pageSize: size,
          cursor: targetOptions.cursor,
          roomId: targetRoomId,
        },
      })
      .send()
      .finally(() => {
        targetOptions.isLoading = false
      })
    if (!data) return
    const computedList = computedTimeBlock(data.list)

    /** 收集需要请求用户详情的 uid */
    const uidCollectYet: Set<number> = new Set() // 去重用
    computedList.forEach((msg) => {
      const replyItem = msg.message.body?.reply
      if (replyItem?.id) {
        const messageIds = targetReplyMap.get(replyItem.id) || []
        messageIds.push(msg.message.id)
        targetReplyMap.set(replyItem.id, messageIds)

        // 查询被回复用户的信息，被回复的用户信息里暂时无 uid
        // collectUidItem(replyItem.uid)
      }
      // 查询消息发送者的信息
      uidCollectYet.add(msg.fromUser.uid)
    })
    // 获取用户信息缓存
    cachedStore.getBatchUserInfo([...uidCollectYet])
    // 为保证获取的历史消息在前面
    const newList = [...computedList, ...targetMessageMap.values()]
    targetMessageMap.clear() // 清空Map
    newList.forEach((msg) => {
      targetMessageMap.set(msg.message.id, msg)
    })

    targetOptions.cursor = data.cursor
    targetOptions.isLast = data.isLast
    targetOptions.isLoading = false

    // 进入会话时如果消息还没加载完，未读分割线的位置在这里补算
    resolveFirstUnread(targetRoomId)
  }

  const getSessionList = async (isFresh = false) => {
    if (!isFresh && (sessionOptions.isLast || sessionOptions.isLoading)) return
    sessionOptions.isLoading = true
    const data = await apis
      .getSessionList({
        params: {
          pageSize: sessionList.length > pageSize ? sessionList.length : pageSize,
          cursor: isFresh || !sessionOptions.cursor ? undefined : sessionOptions.cursor,
        },
      })
      .send()
      .catch(() => {
        sessionOptions.isLoading = false
      })
    if (!data) return
    isFresh
      ? sessionList.splice(0, sessionList.length, ...data.list)
      : sessionList.push(...data.list)
    sessionOptions.cursor = data.cursor
    sessionOptions.isLast = data.isLast
    sessionOptions.isLoading = false

    sortAndUniqueSessionList()

    // 拉取AI助手清单，供@候选人识别、流式回复占位判断使用；不依赖是否已有会话，新用户零会话也要能拿到
    if (!aiBotsFetched && userStore.isSign) {
      aiBotsFetched = true
      cachedStore.fetchAiBots()
    }

    if (sessionList[0]) sessionList[0].unreadCount = 0
    if (!isFirstInit && data.list[0]) {
      isFirstInit = true
      globalStore.currentSession.roomId = data.list[0].roomId
      globalStore.currentSession.type = data.list[0].type
      // 用会话列表第一个去请求消息列表
      getMsgList()
      // 请求第一个群成员列表
      currentRoomType.value === RoomTypeEnum.Group && groupStore.getGroupUserList(true)
      // 初始化所有用户基本信息
      userStore.isSign && cachedStore.initAllUserBaseInfo()
      // 联系人列表
      contactStore.getContactList(true)
    }
  }

  /** 会话列表去重并排序 */
  // activeTime 实际可能是数字时间戳（本地 Date.now() 写入）也可能是后端返回的 ISO 8601 字符串，
  // 字符串直接做减法得到 NaN，会让 sort 的行为完全失控（这就是"会话列表排序时好时坏"的根因），
  // 统一先转毫秒再比较
  const toMillis = (t: number | string): number =>
    typeof t === 'number' ? t : new Date(t).getTime() || 0
  const sortAndUniqueSessionList = () => {
    const temp: Record<string, SessionItem> = {}
    sessionList.forEach((item) => (temp[item.roomId] = item))
    sessionList.splice(0, sessionList.length, ...Object.values(temp))
    sessionList.sort((pre, cur) => {
      const pinDiff = (cur.pinned ? 1 : 0) - (pre.pinned ? 1 : 0)
      if (pinDiff !== 0) return pinDiff
      return toMillis(cur.activeTime) - toMillis(pre.activeTime)
    })
  }

  const updateSession = (roomId: number, roomProps: Partial<SessionItem>) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    session && roomProps && Object.assign(session, roomProps)
    sortAndUniqueSessionList()
  }

  const updateSessionLastActiveTime = (roomId: number, room?: SessionItem) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    if (session) {
      Object.assign(session, { activeTime: Date.now() })
    } else if (room) {
      const newItem = cloneDeep(room)
      newItem.activeTime = Date.now()
      sessionList.unshift(newItem)
    }
    sortAndUniqueSessionList()
  }

  // 通过房间ID获取会话信息
  const getSession = (roomId: number): SessionItem => {
    return sessionList.find((item) => item.roomId === roomId) as SessionItem
  }

  const pushMsg = async (msg: MessageType) => {
    // 真实消息（正数id）到达时，如果来自AI机器人且该房间有正在流式生成的占位消息，先清理占位，用真实消息替换
    if (msg.message.id > 0 && cachedStore.isBotUid(msg.fromUser.uid)) {
      const pendingTempId = streamingPlaceholder.get(msg.message.roomId)
      if (pendingTempId !== undefined) {
        messageMap.get(msg.message.roomId)?.delete(pendingTempId)
        streamingPlaceholder.delete(msg.message.roomId)
      }
    }

    const isNewRoom = !messageMap.has(msg.message.roomId)
    if (isNewRoom) messageMap.set(msg.message.roomId, new Map())
    const current = messageMap.get(msg.message.roomId)
    current?.set(msg.message.id, msg)
    // 重新 set 一次外层 Map，确保嵌套 Map 的原地修改一定能触发依赖它的 computed 重新计算
    if (current) messageMap.set(msg.message.roomId, current)

    // 获取用户信息缓存
    // 尝试取缓存user, 如果有 lastModifyTime 说明缓存过了，没有就一定是要缓存的用户了
    const uid = msg.fromUser.uid
    const cacheUser = cachedStore.userCachedList[uid]
    cachedStore.getBatchUserInfo([uid])

    // 发完消息就要刷新会话列表，
    // 如果当前会话已经置顶了，可以不用刷新
    if (globalStore.currentSession && globalStore.currentSession.roomId !== msg.message.roomId) {
      let result = undefined
      // 如果当前路由不是聊天，就开始拿会话详情，并手动新增一条会话记录
      // if (route?.path && route?.path !== '/') {
      //   globalStore.currentSession.roomId = msg.message.roomId
      //   globalStore.currentSession.type = RoomTypeEnum.Single
      if (isNewRoom) {
        result = await apis.sessionDetail({ id: msg.message.roomId }).send()
      }
      //   Router.push('/')
      // }
      updateSessionLastActiveTime(msg.message.roomId, result)
    }

    // 如果收到的消息里面是艾特自己的就发送系统通知
    if (msg.message.body.atUidList?.includes(userStore.userInfo.uid) && cacheUser) {
      notify({
        name: cacheUser.name as string,
        text: msg.message.body.content,
        icon: cacheUser.avatar as string,
      })
    }

    // tab 在后台获得新消息，就开始闪烁！
    if (document.hidden && !shakeTitle.isShaking) {
      shakeTitle.start()
    }

    if (
      currentNewMsgCount.value &&
      currentNewMsgCount.value?.isStart &&
      typeof currentNewMsgCount.value.count === 'number'
    ) {
      currentNewMsgCount.value.count++
      return
    }

    // 聊天记录计数
    if (currentRoomId.value !== msg.message.roomId) {
      const item = sessionList.find((item) => item.roomId === msg.message.roomId)
      if (item) {
        item.unreadCount += 1
      }
      // 如果新消息的 roomId 和 当前显示的 room 的 Id 一致，就更新已读
    } else {
      // 且当前路由在 聊天 内
      if (route?.path && route?.path === '/') {
        apis.markMsgRead({ roomId: currentRoomId.value }).send()
      }
    }

    // 如果当前路由不是聊天，就开始计数
    if (route?.path && route?.path !== '/') {
      globalStore.unReadMark.newMsgUnreadCount++
    }

    // 聊天列表滚动到底部
    setTimeout(() => {
      // 如果超过一屏了，不自动滚动到最新消息。
      chatListToBottomAction.value?.()
    }, 0)
  }

  // 过滤掉小黑子的发言
  const filterUser = (uid: number) => {
    if (typeof uid !== 'number') return
    for (const messages of messageMap.values()) {
      messages?.forEach((msg) => {
        if (msg.fromUser.uid === uid) {
          messages.delete(msg.message.id)
        }
      })
    }
  }

  const loadMore = async (size?: number) => {
    if (currentMessageOptions.value?.isLast || currentMessageOptions.value?.isLoading) return
    await getMsgList(size)
  }

  const clearNewMsgCount = () => {
    currentNewMsgCount.value && (currentNewMsgCount.value.count = 0)
  }

  // 查找消息在列表里面的索引
  const getMsgIndex = (msgId: number) => {
    if (!msgId || isNaN(Number(msgId))) return -1
    const keys = currentMessageMap.value ? Array.from(currentMessageMap.value.keys()) : []
    return keys.findIndex((key) => key === msgId)
  }

  // 更新点赞、举报数
  const updateMarkCount = (markList: MarkItemType[]) => {
    markList.forEach((mark: MarkItemType) => {
      const { msgId, markType, markCount } = mark

      const msgItem = currentMessageMap.value?.get(msgId)
      if (msgItem) {
        if (markType === MarkEnum.LIKE) {
          msgItem.message.messageMark.likeCount = markCount
        } else if (markType === MarkEnum.DISLIKE) {
          msgItem.message.messageMark.dislikeCount = markCount
        }
      }
    })
  }
  // 更新消息撤回状态
  const updateRecallStatus = (data: RevokedMsgType) => {
    const { msgId } = data
    const message = currentMessageMap.value?.get(msgId)
    if (message && typeof data.recallUid === 'number') {
      message.message.type = MsgEnum.RECALL
      const cacheUser = cachedStore.userCachedList[data.recallUid]
      // 如果撤回者的 id 不等于消息发送人的 id, 或者你本人就是管理员，那么显示管理员撤回的。
      if (data.recallUid !== message.fromUser.uid) {
        message.message.body = `管理员"${cacheUser.name}"撤回了一条成员消息` // 后期根据本地用户数据修改
      } else {
        // 如果被撤回的消息是消息发送者撤回，正常显示
        message.message.body = `"${cacheUser.name}"撤回了一条消息` // 后期根据本地用户数据修改
      }
    }
    // 更新与这条撤回消息有关的消息
    const messageList = currentReplyMap.value?.get(msgId)
    messageList?.forEach((id) => {
      const msg = currentMessageMap.value?.get(id)
      if (msg) {
        msg.message.body.reply.body = `原消息已被撤回`
      }
    })
  }
  // 删除消息
  const deleteMsg = (msgId: number) => {
    currentMessageMap.value?.delete(msgId)
  }
  // 更新已编辑消息，不触发新消息未读和通知逻辑
  const updateEditedMsg = (msg: MessageType) => {
    const roomMessages = messageMap.get(msg.message.roomId)
    if (roomMessages?.has(msg.message.id)) {
      roomMessages.set(msg.message.id, msg)
      messageMap.set(msg.message.roomId, roomMessages)
    }
    const session = sessionList.find((item) => item.roomId === msg.message.roomId)
    if (session) {
      session.text = `${msg.fromUser.username || msg.fromUser.uid}:${
        msg.message.body?.content || ''
      }`
    }
  }
  // 更新消息
  const updateMsg = (msgId: number, newMessage: MessageType) => {
    deleteMsg(msgId)
    pushMsg(newMessage)
  }

  /**
   * 处理AI流式回复片段：首个片段创建临时占位消息，后续片段原地追加内容。
   * 不依赖 currentRoomId/currentMessageMap，显式操作 data.roomId 对应的Map——
   * 用户切到别的会话时，流式推送仍要能正确落到目标房间，不能串到当前正在看的房间。
   */
  const handleAgentStreamChunk = (data: {
    roomId: number
    streamId: string
    fromUid: number
    chunk: string
  }) => {
    const { roomId, fromUid, chunk } = data
    const existingTempId = streamingPlaceholder.get(roomId)
    if (existingTempId !== undefined) {
      appendStreamingContent(roomId, existingTempId, chunk)
      return
    }
    const tempId = -Date.now()
    const placeholderMsg = {
      fromUser: { uid: fromUid, username: '', avatar: '', locPlace: '', isBot: true },
      message: {
        id: tempId,
        roomId,
        type: MsgEnum.TEXT,
        body: { content: chunk, urlContentMap: {}, atUidList: null, reply: null },
        sendTime: Date.now(),
        messageMark: { likeCount: 0, userLike: 0, dislikeCount: 0, userDislike: 0 },
      },
      sendTime: String(Date.now()),
      streaming: true,
      // 停止生成按钮需要它来调 stopAiStream 接口
      streamId: data.streamId,
    } as MessageType
    pushMsg(placeholderMsg)
    streamingPlaceholder.set(roomId, tempId)
  }

  const appendStreamingContent = (roomId: number, tempId: number, chunk: string) => {
    const msg = messageMap.get(roomId)?.get(tempId)
    if (msg) {
      // reactive深层代理，直接改字段即可触发视图更新，不需要删除重建
      msg.message.body.content += chunk
    }
  }

  // 标记已读数为 0
  const markSessionRead = (roomId: number) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    const unreadCount = session?.unreadCount || 0
    if (session) {
      session.unreadCount = 0
    }
    // 记录未读分割线位置：有未读时定位第一条未读消息，无未读时清掉旧分割线
    if (unreadCount > 0) {
      pendingUnreadCount.set(roomId, unreadCount)
      resolveFirstUnread(roomId)
    } else {
      pendingUnreadCount.delete(roomId)
      firstUnreadMsgId.delete(roomId)
    }
    return unreadCount
  }

  // 根据消息id获取消息体
  const getMessage = (messageId: number) => {
    return currentMessageMap.value?.get(messageId)
  }

  // 删除会话
  const removeContact = (roomId: number) => {
    const index = sessionList.findIndex((session) => session.roomId === roomId)
    sessionList.splice(index, 1)
  }

  return {
    getMsgIndex,
    chatMessageList,
    firstUnreadMsgId,
    pushMsg,
    deleteMsg,
    clearNewMsgCount,
    updateMarkCount,
    updateRecallStatus,
    updateEditedMsg,
    updateMsg,
    handleAgentStreamChunk,
    chatListToBottomAction,
    newMsgCount,
    messageMap,
    currentMessageMap,
    currentMessageOptions,
    currentReplyMap,
    currentNewMsgCount,
    loadMore,
    currentMsgReply,
    filterUser,
    sessionList,
    sessionOptions,
    getSessionList,
    updateSession,
    updateSessionLastActiveTime,
    markSessionRead,
    getSession,
    isGroup,
    currentSessionInfo,
    getMessage,
    removeContact,
  }
})
