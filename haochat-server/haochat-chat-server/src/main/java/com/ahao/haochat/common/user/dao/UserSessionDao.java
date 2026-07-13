package com.ahao.haochat.common.user.dao;

import com.ahao.haochat.common.user.domain.entity.UserSession;
import com.ahao.haochat.common.user.mapper.UserSessionMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserSessionDao extends ServiceImpl<UserSessionMapper, UserSession> {

    public UserSession getActiveSession(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return lambdaQuery()
                .eq(UserSession::getId, sessionId)
                .eq(UserSession::getStatus, 0)
                .gt(UserSession::getExpireTime, new Date())
                .one();
    }

    public void revoke(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        UserSession update = new UserSession();
        update.setId(sessionId);
        update.setStatus(1);
        update.setUpdateTime(new Date());
        updateById(update);
    }

    public void revokeByUid(Long uid) {
        if (uid == null) {
            return;
        }
        lambdaUpdate()
                .eq(UserSession::getUid, uid)
                .eq(UserSession::getStatus, 0)
                .set(UserSession::getStatus, 1)
                .set(UserSession::getUpdateTime, new Date())
                .update();
    }
}
