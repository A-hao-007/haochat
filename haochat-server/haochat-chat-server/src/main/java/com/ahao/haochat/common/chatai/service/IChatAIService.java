package com.ahao.haochat.common.chatai.service;

import com.ahao.haochat.common.chat.domain.entity.Message;

public interface IChatAIService {

    void chat(Message message);
}
