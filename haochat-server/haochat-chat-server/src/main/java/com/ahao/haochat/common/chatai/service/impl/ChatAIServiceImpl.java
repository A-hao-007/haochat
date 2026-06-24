package com.ahao.haochat.common.chatai.service.impl;

import com.ahao.haochat.common.chat.dao.RoomFriendDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.entity.msg.MessageExtra;
import com.ahao.haochat.common.chatai.handler.AbstractChatAIHandler;
import com.ahao.haochat.common.chatai.handler.ChatAIHandlerFactory;
import com.ahao.haochat.common.chatai.service.IChatAIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatAIServiceImpl implements IChatAIService {

    @Autowired
    private RoomFriendDao roomFriendDao;

    @Override
    public void chat(Message message) {
        MessageExtra extra = message.getExtra();
        if (extra == null) {
            extra = new MessageExtra();
        }

        // 1. 先检查 @ 列表里有没有AI用户
        AbstractChatAIHandler chatAI = ChatAIHandlerFactory.getChatAIHandlerById(extra.getAtUidList());
        
        // 2. 如果没有@，检查是否为与AI的单聊房间，自动触发AI
        if (chatAI == null) {
            List<Long> aiUids = ChatAIHandlerFactory.getAllAIUserIds();
            RoomFriend friendRoom = roomFriendDao.getByRoomId(message.getRoomId());
            if (friendRoom != null) {
                Long otherUid = Objects.equals(friendRoom.getUid1(), message.getFromUid())
                        ? friendRoom.getUid2() : friendRoom.getUid1();
                if (aiUids.contains(otherUid)) {
                    chatAI = ChatAIHandlerFactory.getChatAIHandlerById(otherUid);
                }
            }
        }

        if (chatAI != null) {
            chatAI.chat(message);
        }
    }
}