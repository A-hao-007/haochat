package com.ahao.haochat.common.chat.dao;

import com.ahao.haochat.common.chat.domain.entity.MessageMark;
import com.ahao.haochat.common.chat.mapper.MessageMarkMapper;
import com.ahao.haochat.common.common.domain.enums.NormalOrNoEnum;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 消息标记表 服务实现类
 * </p>
 *
 * @author A-hao</a>
 * @since 2023-04-08
 */
@Service
public class MessageMarkDao extends ServiceImpl<MessageMarkMapper, MessageMark> {

    public MessageMark get(Long uid, Long msgId, Integer markType) {
        return lambdaQuery().eq(MessageMark::getUid, uid)
                .eq(MessageMark::getMsgId, msgId)
                .eq(MessageMark::getType, markType)
                .one();
    }

    public Integer getMarkCount(Long msgId, Integer markType) {
        return lambdaQuery().eq(MessageMark::getMsgId, msgId)
                .eq(MessageMark::getType, markType)
                .eq(MessageMark::getStatus, NormalOrNoEnum.NORMAL.getStatus())
                .count();
    }

    public List<MessageMark> getValidMarkByMsgIdBatch(List<Long> msgIds) {
        return lambdaQuery()
                .in(MessageMark::getMsgId, msgIds)
                .eq(MessageMark::getStatus, NormalOrNoEnum.NORMAL.getStatus())
                .list();
    }
}
