package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.common.event.UserBlackEvent;
import com.ahao.haochat.common.user.domain.enums.WSBaseResp;
import com.ahao.haochat.common.user.domain.enums.WSRespTypeEnum;
import com.ahao.haochat.common.user.domain.vo.response.ws.WSBlack;
import com.ahao.haochat.common.user.service.WebSocketService;
import com.ahao.haochat.common.user.service.cache.UserCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用户拉黑监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class UserBlackListener {
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private WebSocketService webSocketService;
    @Autowired
    private UserCache userCache;

    @Async
    @EventListener(classes = UserBlackEvent.class)
    public void refreshRedis(UserBlackEvent event) {
        userCache.evictBlackMap();
        userCache.remove(event.getUser().getId());
    }

    @Async
    @EventListener(classes = UserBlackEvent.class)
    public void deleteMsg(UserBlackEvent event) {
        messageDao.invalidByUid(event.getUser().getId());
    }

    @Async
    @EventListener(classes = UserBlackEvent.class)
    public void sendPush(UserBlackEvent event) {
        Long uid = event.getUser().getId();
        WSBaseResp<WSBlack> resp = new WSBaseResp<>();
        WSBlack black = new WSBlack(uid);
        resp.setData(black);
        resp.setType(WSRespTypeEnum.BLACK.getType());
        webSocketService.sendToAllOnline(resp, uid);
    }


}
