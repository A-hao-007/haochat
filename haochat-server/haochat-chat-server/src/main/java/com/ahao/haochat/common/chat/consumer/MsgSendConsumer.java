package com.ahao.haochat.common.chat.consumer;

import com.ahao.haochat.common.chat.dao.ContactDao;
import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.dao.RoomDao;
import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.Room;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.RoomTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.response.ChatMessageResp;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.ContactRefreshBuffer;
import com.ahao.haochat.common.chat.service.WeChatMsgOperationService;
import com.ahao.haochat.common.chat.service.cache.GroupMemberCache;
import com.ahao.haochat.common.chat.service.cache.HotRoomCache;
import com.ahao.haochat.common.chat.service.cache.RoomCache;
import com.ahao.haochat.common.chatai.service.IChatAIService;
import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.domain.dto.MsgSendMessageDTO;
import com.ahao.haochat.common.user.service.WebSocketService;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.cache.UserCache;
import com.ahao.haochat.common.user.service.impl.PushService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Description: 发送消息更新房间收信箱，并同步给房间成员信箱
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-08-12
 */
// consumeThreadNumber/consumeThreadMax 显式收紧：默认值(20/64)是给多核大流量场景准备的，
// 2核小容器+4个consumer同时用默认值会导致上百条常驻线程，是本机线程数偏高的主因之一
@RocketMQMessageListener(consumerGroup = MQConstant.SEND_MSG_GROUP, topic = MQConstant.SEND_MSG_TOPIC,
        consumeThreadNumber = 2, consumeThreadMax = 4)
@Component
@Slf4j
public class MsgSendConsumer implements RocketMQListener<MsgSendMessageDTO> {
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
    private PushService pushService;
    @Autowired
    private ContactRefreshBuffer contactRefreshBuffer;

    @Override
    public void onMessage(MsgSendMessageDTO dto) {
        Message message = messageDao.getById(dto.getMsgId());
        if (message == null) {
            log.warn("onMessage: message not found, msgId={}", dto.getMsgId());
            return;
        }
        Room room = roomCache.get(message.getRoomId());
        if (room == null) {
            log.warn("onMessage: room not found, roomId={}", message.getRoomId());
            return;
        }
        //所有房间更新房间最新消息
        roomDao.refreshActiveTime(room.getId(), message.getId(), message.getCreateTime());
        //原先这里每条消息 delete 房间缓存，导致越活跃的房间缓存命中率越低（刚删就被下一次读回源再删）。
        //本次变更只涉及 lastMsgId/activeTime 两个弱一致字段，改为原地回填：并发时偶发的旧值覆盖
        //会被下一条消息自愈，且缓存本身有 5 分钟 TTL 兜底。
        room.setLastMsgId(message.getId());
        room.setActiveTime(message.getCreateTime());
        roomCache.put(room.getId(), room);
        //注意：WS 推送已由 MessageSendListener.pushToOnlineClients 在事务提交后直推（broker 故障期的
        //止血方案，修复后成为主路径）。此消费者曾同时推送导致每条消息被推两遍（客户端按 msgId 去重所以
        //用户无感，但服务端推送负载翻倍），现统一收敛到直推路径，本消费者只负责数据类异步任务。
        if (room.isHotRoom()) {
            //更新热门群聊时间-redis
            hotRoomCache.refreshActiveTime(room.getId(), message.getCreateTime());
        } else {
            //全员会话刷新走合并缓冲：同房间窗口期内多条消息只刷一次（写扩散去抖，
            //N 人群从"每条消息 N 行 upsert"降为"每窗口 N 行"）。合并语义见 ContactRefreshBuffer
            contactRefreshBuffer.submit(room.getId(), message.getId(), message.getCreateTime());
        }
    }


}
