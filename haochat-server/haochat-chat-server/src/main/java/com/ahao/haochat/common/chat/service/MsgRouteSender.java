package com.ahao.haochat.common.chat.service;

import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.domain.dto.MsgSendMessageDTO;
import com.ahao.haochat.transaction.service.MQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息路由 MQ 发送的最小封装。
 * 独立成 bean 是为了让失败补偿（SecureInvokeRecord 反射重放）有一个参数类型
 * 简单（Long）、可精确定位的方法签名——直接重放 MQProducer.sendMsg(String, Object)
 * 会在 JSON 反序列化 Object 参数时退化成 LinkedHashMap，存在载荷变形风险。
 */
@Component
public class MsgRouteSender {
    @Autowired
    private MQProducer mqProducer;

    public void send(Long msgId) {
        mqProducer.sendMsg(MQConstant.SEND_MSG_TOPIC, new MsgSendMessageDTO(msgId));
    }
}
