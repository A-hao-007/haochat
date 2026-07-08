package com.ahao.haochat.common.user.consumer;

import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.domain.dto.LoginMessageDTO;
import com.ahao.haochat.common.user.service.WebSocketService;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Description: 在本地服务上找寻对应channel，将对应用户登陆，并触发所有用户收到上线事件
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-08-12
 */
@RocketMQMessageListener(consumerGroup = MQConstant.LOGIN_MSG_GROUP, topic = MQConstant.LOGIN_MSG_TOPIC, messageModel = MessageModel.BROADCASTING,
        consumeThreadNumber = 1, consumeThreadMax = 2)
@Component
public class MsgLoginConsumer implements RocketMQListener<LoginMessageDTO> {
    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void onMessage(LoginMessageDTO loginMessageDTO) {
        //尝试登录登录
        webSocketService.scanLoginSuccess(loginMessageDTO.getCode(), loginMessageDTO.getUid());
    }

}
