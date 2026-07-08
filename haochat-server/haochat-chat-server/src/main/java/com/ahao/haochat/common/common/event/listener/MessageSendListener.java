package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.dao.RoomDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.HotFlagEnum;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.WeChatMsgOperationService;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.chat.service.cache.HotRoomCache;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import com.ahao.haochat.common.chatai.service.IChatAIService;
import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.domain.dto.MsgSendMessageDTO;
import com.ahao.haochat.common.common.event.MessageSendEvent;
import com.ahao.haochat.common.user.service.WebSocketService;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.cache.UserCache;
import com.ahao.haochat.common.user.service.impl.PushService;
import com.ahao.haochat.transaction.service.MQProducer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 消息发送监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class MessageSendListener {
    @Autowired
    private WebSocketService webSocketService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private IChatAIService openAIService;
    @Autowired
    WeChatMsgOperationService weChatMsgOperationService;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private RoomDao roomDao;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private UserCache userCache;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private ContactDao contactDao;
    @Autowired
    private HotRoomCache hotRoomCache;
    @Autowired
    private MQProducer mqProducer;
    @Autowired
    private PushService pushService;

    /**
     * 发送到 MQ（用于 room/contact 更新等异步任务）
     * 用 AFTER_COMMIT 而不是 BEFORE_COMMIT：消费者收到消息后会立刻查 message 表，
     * BEFORE_COMMIT 阶段本地事务还没真正提交，消费速度足够快时会出现"查不到这条消息"的竞态。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void messageRoute(MessageSendEvent event) {
        Long msgId = event.getMsgId();
        mqProducer.sendMsg(MQConstant.SEND_MSG_TOPIC, new MsgSendMessageDTO(msgId));
    }

    /**
     * 直接推送到在线 WebSocket 客户端（不依赖 RocketMQ 消费）
     * 事务提交后执行，确保消息已落库
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void pushToOnlineClients(@NotNull MessageSendEvent event) {
        try {
            Message message = messageDao.getById(event.getMsgId());
            if (message == null) {
                log.warn("pushToOnlineClients: message not found, msgId={}", event.getMsgId());
                return;
            }
            Room room = roomCache.get(message.getRoomId());
            if (room == null) {
                log.warn("pushToOnlineClients: room not found, roomId={}", message.getRoomId());
                return;
            }
            ChatMessageResp msgResp = chatService.getMsgResp(message, null);
            if (room.isHotRoom()) {
                pushService.sendPushMsg(WSAdapter.buildMsgSend(msgResp));
            } else {
                List<Long> memberUidList = new ArrayList<>();
                if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {
                    memberUidList = groupMemberCache.getMemberUidList(room.getId());
                } else if (Objects.equals(room.getType(), RoomTypeEnum.FRIEND.getType())) {
                    RoomFriend roomFriend = roomFriendDao.getByRoomId(room.getId());
                    if (roomFriend != null) {
                        memberUidList = Arrays.asList(roomFriend.getUid1(), roomFriend.getUid2());
                    }
                }
                if (!memberUidList.isEmpty()) {
                    pushService.sendPushMsg(WSAdapter.buildMsgSend(msgResp), memberUidList);
                }
            }
        } catch (Exception e) {
            log.error("pushToOnlineClients error, msgId={}", event.getMsgId(), e);
        }
    }

    @TransactionalEventListener(classes = MessageSendEvent.class, fallbackExecution = true)
    public void handlerMsg(@NotNull MessageSendEvent event) {
        Message message = messageDao.getById(event.getMsgId());
        // AI 在任何房间被 @ 时都会尝试回复
        openAIService.chat(message);
    }

    public boolean isHotRoom(Room room) {
        return Objects.equals(HotFlagEnum.YES.getType(), room.getHotFlag());
    }

    /**
     * 给用户微信推送艾特好友的消息通知
     * （这个没开启，微信不让推）
     */
    @TransactionalEventListener(classes = MessageSendEvent.class, fallbackExecution = true)
    public void publishChatToWechat(@NotNull MessageSendEvent event) {
        Message message = messageDao.getById(event.getMsgId());
        if (Objects.nonNull(message.getExtra().getAtUidList())) {
            weChatMsgOperationService.publishChatMsgToWeChatUser(message.getFromUid(), message.getExtra().getAtUidList(),
                    message.getContent());
        }
    }
}
