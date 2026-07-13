package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.chat.service.cache.MsgCache;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import com.ahao.haochat.common.common.event.MessageEditEvent;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.impl.PushService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class MessageEditListener {
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MsgCache msgCache;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private PushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = MessageEditEvent.class, fallbackExecution = true)
    public void pushEditedMessage(@NotNull MessageEditEvent event) {
        msgCache.evictMsg(event.getMsgId());
        try {
            Message message = messageDao.getById(event.getMsgId());
            if (message == null) {
                log.warn("pushEditedMessage: message not found, msgId={}", event.getMsgId());
                return;
            }
            Room room = roomCache.get(message.getRoomId());
            if (room == null) {
                log.warn("pushEditedMessage: room not found, roomId={}", message.getRoomId());
                return;
            }
            ChatMessageResp msgResp = chatService.getMsgResp(message, null);
            if (room.isHotRoom()) {
                pushService.sendPushMsg(WSAdapter.buildMsgEdit(msgResp));
                return;
            }
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
                pushService.sendPushMsg(WSAdapter.buildMsgEdit(msgResp), memberUidList);
            }
        } catch (Exception e) {
            log.error("pushEditedMessage error, msgId={}", event.getMsgId(), e);
        }
    }
}
