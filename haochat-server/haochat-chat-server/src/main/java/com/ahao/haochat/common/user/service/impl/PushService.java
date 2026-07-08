package com.ahao.haochat.common.user.service.impl;

import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Description: 消息推送。
 * 单实例部署下直接投递到本地在线 channel（之前经 RocketMQ BROADCASTING 中转，
 * 广播消费端不可靠导致消息不实时——改为直推，最小且可靠）。
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-08-12
 */
@Slf4j
@Service
public class PushService {
    @Autowired
    private WebSocketService webSocketService;

    public void sendPushMsg(WSBaseResp<?> msg, List<Long> uidList) {
        if (uidList == null) {
            return;
        }
        log.debug("PushService 推送消息 type={} 给 {} 个用户", msg.getType(), uidList.size());
        uidList.forEach(uid -> webSocketService.sendToUid(msg, uid));
    }

    public void sendPushMsg(WSBaseResp<?> msg, Long uid) {
        log.debug("PushService 推送消息 type={} 给用户 uid={}", msg.getType(), uid);
        webSocketService.sendToUid(msg, uid);
    }

    /**
     * 保序推送：不经过线程池，调用方连续调用几次就按几次的顺序真正发出去。
     * 高频密集场景（如 AI 流式回复片段）必须用这个，否则线程池不保证任务执行顺序，客户端会收到乱序内容。
     */
    public void sendPushMsgSync(WSBaseResp<?> msg, Long uid) {
        webSocketService.sendToUidSync(msg, uid);
    }

    public void sendPushMsg(WSBaseResp<?> msg) {
        log.debug("PushService 推送消息 type={} 给所有在线用户", msg.getType());
        webSocketService.sendToAllOnline(msg);
    }
}
