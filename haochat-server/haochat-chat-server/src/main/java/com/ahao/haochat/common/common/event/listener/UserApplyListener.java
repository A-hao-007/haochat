package com.ahao.haochat.common.common.event.listener;

import com.ahao.haochat.common.common.event.UserApplyEvent;
import com.ahao.haochat.common.user.dao.UserApplyDao;
import com.ahao.haochat.common.user.domain.entity.UserApply;
import com.ahao.haochat.common.user.domain.vo.response.ws.WSFriendApply;
import com.ahao.haochat.common.user.service.WebSocketService;
import com.ahao.haochat.common.user.service.adapter.WSAdapter;
import com.ahao.haochat.common.user.service.impl.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 好友申请监听器
 *
 * @author zhongzb create on 2022/08/26
 */
@Slf4j
@Component
public class UserApplyListener {
    @Autowired
    private UserApplyDao userApplyDao;
    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private PushService pushService;

    @Async
    @TransactionalEventListener(classes = UserApplyEvent.class, fallbackExecution = true)
    public void notifyFriend(UserApplyEvent event) {
        UserApply userApply = event.getUserApply();
        Integer unReadCount = userApplyDao.getUnReadCount(userApply.getTargetId());
        pushService.sendPushMsg(WSAdapter.buildApplySend(new WSFriendApply(userApply.getUid(), unReadCount)), userApply.getTargetId());
    }

}
