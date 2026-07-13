package com.ahao.haochat.common.chat.dao;

import com.ahao.haochat.common.chat.domain.entity.GroupOperationLog;
import com.ahao.haochat.common.chat.domain.enums.GroupOperationTypeEnum;
import com.ahao.haochat.common.chat.mapper.GroupOperationLogMapper;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.common.utils.CursorUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GroupOperationLogDao extends ServiceImpl<GroupOperationLogMapper, GroupOperationLog> {

    public void record(Long roomId, Long groupId, Long operatorUid, Long targetUid, GroupOperationTypeEnum type, String content) {
        save(GroupOperationLog.builder()
                .roomId(roomId)
                .groupId(groupId)
                .operatorUid(operatorUid)
                .targetUid(targetUid)
                .type(type.getType())
                .content(content)
                .createTime(new Date())
                .build());
    }

    public CursorPageBaseResp<GroupOperationLog> page(Long roomId, CursorPageBaseReq request) {
        return CursorUtils.getCursorPageByMysql(this, request, wrapper -> {
            wrapper.eq(GroupOperationLog::getRoomId, roomId);
        }, GroupOperationLog::getId);
    }
}
