package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.chat.domain.dto.ChatMessageMarkDTO;
import com.ahao.haochat.common.chat.domain.enums.MessageMarkTypeEnum;
import com.ahao.haochat.common.common.constant.MQEventType;
import com.ahao.haochat.common.common.event.GroupMemberAddEvent;
import com.ahao.haochat.common.common.event.FileUploadCompletedEvent;
import com.ahao.haochat.common.common.event.FriendApplyAcceptedEvent;
import com.ahao.haochat.common.common.event.GroupMemberLeftEvent;
import com.ahao.haochat.common.common.event.ItemReceiveEvent;
import com.ahao.haochat.common.common.event.MessageEditEvent;
import com.ahao.haochat.common.common.event.MessageMarkEvent;
import com.ahao.haochat.common.common.event.MessageRecallEvent;
import com.ahao.haochat.common.common.event.MessageSendEvent;
import com.ahao.haochat.common.common.event.UserApplyEvent;
import com.ahao.haochat.common.common.event.UserBlackEvent;
import com.ahao.haochat.common.common.event.UserOfflineEvent;
import com.ahao.haochat.common.common.event.UserOnlineEvent;
import com.ahao.haochat.common.common.event.UserRegisterEvent;
import com.ahao.haochat.common.common.service.BusinessEventPublisher;
import com.ahao.haochat.common.user.domain.entity.UserApply;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Converts in-process domain events into reliable MQ events. */
@Component
@RequiredArgsConstructor
public class BusinessEventMqBridge {
    private final BusinessEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void messageCreated(MessageSendEvent event) {
        publish(MQEventType.NEW_MESSAGE, event.getMsgId(), null, map("msgId", event.getMsgId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = UserApplyEvent.class, fallbackExecution = true)
    public void friendApplyCreated(UserApplyEvent event) {
        UserApply apply = event.getUserApply();
        publish(MQEventType.FRIEND_APPLY, apply.getId(), apply.getUid(), map(
                "applyId", apply.getId(), "uid", apply.getUid(), "targetId", apply.getTargetId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = GroupMemberAddEvent.class, fallbackExecution = true)
    public void groupMemberJoined(GroupMemberAddEvent event) {
        publish(MQEventType.GROUP_MEMBER_JOINED, event.getRoomGroup().getRoomId(), event.getInviteUid(), map(
                "roomId", event.getRoomGroup().getRoomId(),
                "groupId", event.getRoomGroup().getId(),
                "memberUids", event.getMemberList().stream().map(member -> member.getUid()).collect(Collectors.toList())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = FileUploadCompletedEvent.class, fallbackExecution = true)
    public void fileUploadCompleted(FileUploadCompletedEvent event) {
        publish(MQEventType.FILE_UPLOAD_COMPLETED, event.getAssetId(), null, map("assetId", event.getAssetId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = FriendApplyAcceptedEvent.class, fallbackExecution = true)
    public void friendApplyAccepted(FriendApplyAcceptedEvent event) {
        publish(MQEventType.FRIEND_APPLY_ACCEPTED, event.getApplyId(), event.getUid(), map(
                "applyId", event.getApplyId(), "uid", event.getUid(), "friendUid", event.getFriendUid(), "roomId", event.getRoomId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = GroupMemberLeftEvent.class, fallbackExecution = true)
    public void groupMemberLeft(GroupMemberLeftEvent event) {
        publish(MQEventType.GROUP_MEMBER_LEFT, event.getGroupId(), event.getOperatorUid(), map(
                "groupId", event.getGroupId(), "roomId", event.getRoomId(), "memberUid", event.getMemberUid()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = MessageMarkEvent.class, fallbackExecution = true)
    public void messageMarked(MessageMarkEvent event) {
        ChatMessageMarkDTO dto = event.getDto();
        String type = MessageMarkTypeEnum.DISLIKE.getType().equals(dto.getMarkType())
                ? MQEventType.MESSAGE_REPORTED : MQEventType.MESSAGE_MARKED;
        publish(type, dto.getMsgId(), dto.getUid(), dto);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = MessageRecallEvent.class, fallbackExecution = true)
    public void messageRecalled(MessageRecallEvent event) {
        publish(MQEventType.MESSAGE_RECALLED, event.getRecallDTO().getMsgId(), null, event.getRecallDTO());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = MessageEditEvent.class, fallbackExecution = true)
    public void messageEdited(MessageEditEvent event) {
        publish(MQEventType.MESSAGE_EDITED, event.getMsgId(), null, map("msgId", event.getMsgId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = UserRegisterEvent.class, fallbackExecution = true)
    public void userRegistered(UserRegisterEvent event) {
        publish(MQEventType.USER_REGISTERED, event.getUser().getId(), event.getUser().getId(), map("uid", event.getUser().getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = UserOnlineEvent.class, fallbackExecution = true)
    public void userOnline(UserOnlineEvent event) {
        publish(MQEventType.USER_ONLINE, event.getUser().getId(), event.getUser().getId(), map("uid", event.getUser().getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = UserOfflineEvent.class, fallbackExecution = true)
    public void userOffline(UserOfflineEvent event) {
        publish(MQEventType.USER_OFFLINE, event.getUser().getId(), event.getUser().getId(), map("uid", event.getUser().getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = UserBlackEvent.class, fallbackExecution = true)
    public void userBlacklisted(UserBlackEvent event) {
        publish(MQEventType.USER_BLACKLISTED, event.getUser().getId(), event.getUser().getId(), map("uid", event.getUser().getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = ItemReceiveEvent.class, fallbackExecution = true)
    public void itemReceived(ItemReceiveEvent event) {
        publish(MQEventType.ITEM_RECEIVED, event.getUserBackpack().getId(), event.getUserBackpack().getUid(), event.getUserBackpack());
    }

    private void publish(String eventType, Object aggregateId, Long operatorUid, Object payload) {
        publisher.publish(eventType, aggregateId == null ? null : String.valueOf(aggregateId), operatorUid, payload);
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
