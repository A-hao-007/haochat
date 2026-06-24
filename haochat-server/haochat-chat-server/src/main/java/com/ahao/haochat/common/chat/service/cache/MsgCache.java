package com.ahao.haochat.common.chat.service.cache;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.user.dao.BlackDao;
import com.ahao.haochat.common.user.dao.RoleDao;
import com.ahao.haochat.common.user.dao.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Description: 消息相关缓存
 * Author: <a href="https://github.com/A-hao-007">abin</a>
 * Date: 2023-03-27
 */
@Component
public class MsgCache {

    @Autowired
    private UserDao userDao;
    @Autowired
    private BlackDao blackDao;
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private MessageDao messageDao;

    @Cacheable(cacheNames = "msg", key = "'msg'+#msgId")
    public Message getMsg(Long msgId) {
        return messageDao.getById(msgId);
    }

    @CacheEvict(cacheNames = "msg", key = "'msg'+#msgId")
    public Message evictMsg(Long msgId) {
        return null;
    }
}
