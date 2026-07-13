package com.ahao.haochat.common.audit.dao;

import com.ahao.haochat.common.audit.domain.AuditLog;
import com.ahao.haochat.common.audit.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AuditLogDao extends ServiceImpl<AuditLogMapper, AuditLog> {
    public AuditLog getByEventId(String eventId) {
        return lambdaQuery().eq(AuditLog::getEventId, eventId).one();
    }
}
