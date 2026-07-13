package com.ahao.haochat.common.common.event.listener;

import cn.hutool.core.date.DateUtil;
import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.MsgRouteSender;
import com.ahao.haochat.common.chat.service.WeChatMsgOperationService;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import com.ahao.haochat.common.chatai.service.IChatAIService;
import com.ahao.haochat.common.chatai.memory.AiMemoryExtractionService;
import com.ahao.haochat.common.common.event.MessageSendEvent;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.impl.PushService;
import com.ahao.haochat.transaction.domain.dto.SecureInvokeDTO;
import com.ahao.haochat.transaction.domain.entity.SecureInvokeRecord;
import com.ahao.haochat.transaction.service.SecureInvokeService;
import com.ahao.haochat.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
    private ChatService chatService;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private IChatAIService openAIService;
    @Autowired
    private AiMemoryExtractionService aiMemoryExtractionService;
    @Autowired
    WeChatMsgOperationService weChatMsgOperationService;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private PushService pushService;
    @Autowired
    private MsgRouteSender msgRouteSender;
    @Autowired
    private SecureInvokeService secureInvokeService;

    /**
     * 发送到 MQ（用于 room/contact 更新等异步任务）——乐观直发 + 失败落表补偿。
     *
     * 演进记录（面试素材）：曾改为 BEFORE_COMMIT + @SecureInvoke 的教科书式本地消息表
     * （每条消息事务内 insert + 成功后 delete），压测显示行级 churn 与事务变长使发送 P95
     * 从 955ms 恶化到 2.06s、读取 P95 从 105ms 恶化到 1.39s——可靠性成本必须按失败概率分配：
     * 正常路径（broker 可用，>99.9%）直发零额外开销；仅发送异常时才写 secure_invoke_record，
     * 交给 SecureInvokeService 既有的 5s 补偿扫描 + 指数退避重试。
     * 剩余风险窗口：事务提交后、直发完成前进程崩溃会丢这一次路由（与改造前相同），
     * 由"下一条消息自愈会话时间"兜底。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void messageRoute(MessageSendEvent event) {
        Long msgId = event.getMsgId();
        try {
            msgRouteSender.send(msgId);
        } catch (Exception e) {
            log.error("MQ 消息路由直发失败，落补偿记录等待重试 msgId={}", msgId, e);
            saveRouteCompensation(msgId);
        }
    }

    /**
     * 构造指向 MsgRouteSender.send(Long) 的补偿记录，由 SecureInvokeService 定时反射重放
     */
    private void saveRouteCompensation(Long msgId) {
        try {
            SecureInvokeDTO dto = SecureInvokeDTO.builder()
                    .className(MsgRouteSender.class.getName())
                    .methodName("send")
                    .parameterTypes(JsonUtils.toStr(Collections.singletonList(Long.class.getName())))
                    .args(JsonUtils.toStr(new Object[]{msgId}))
                    .build();
            SecureInvokeRecord record = SecureInvokeRecord.builder()
                    .secureInvokeDTO(dto)
                    .maxRetryTimes(5)
                    //首次重试 30s 后（broker 抖动通常秒级恢复），之后指数退避
                    .nextRetryTime(DateUtil.offsetSecond(new Date(), 30))
                    .build();
            secureInvokeService.save(record);
        } catch (Exception ex) {
            log.error("补偿记录写入失败，msgId={} 的路由将依赖下一条消息自愈", msgId, ex);
        }
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
        // AI execution is consumed from the reliable business-event topic.
        if (event.getMsgId() != null) {
            return;
        }
        Message message = messageDao.getById(event.getMsgId());
        // AI 在任何房间被 @ 时都会尝试回复
        openAIService.chat(message);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void captureLongTermMemory(@NotNull MessageSendEvent event) {
        Message message = messageDao.getById(event.getMsgId());
        if (message != null) {
            aiMemoryExtractionService.capture(message);
        }
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
