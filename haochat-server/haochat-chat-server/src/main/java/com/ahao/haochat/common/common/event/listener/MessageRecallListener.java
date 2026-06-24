package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.chat.domain.dto.ChatMsgRecallDTO;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.cache.MsgCache;
import com.ahao.haochat.common.common.event.MessageRecallEvent;
import com.ahao.haochat.common.user.service.WebSocketService;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.impl.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 消息撤回监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class MessageRecallListener {
    @Autowired
    private WebSocketService webSocketService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MsgCache msgCache;
    @Autowired
    private PushService pushService;

    @Async
    @TransactionalEventListener(classes = MessageRecallEvent.class, fallbackExecution = true)
    public void evictMsg(MessageRecallEvent event) {
        ChatMsgRecallDTO recallDTO = event.getRecallDTO();
        msgCache.evictMsg(recallDTO.getMsgId());
    }

    @Async
    @TransactionalEventListener(classes = MessageRecallEvent.class, fallbackExecution = true)
    public void sendToAll(MessageRecallEvent event) {
        pushService.sendPushMsg(WSAdapter.buildMsgRecall(event.getRecallDTO()));
    }

}
