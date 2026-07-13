package com.ahao.haochat.common.audit.consumer;

import com.ahao.haochat.common.audit.dao.AuditLogDao;
import com.ahao.haochat.common.audit.domain.AuditLog;
import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.domain.dto.MQBusinessEvent;
import com.ahao.haochat.common.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Durable asynchronous audit sink for every published business event. */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(consumerGroup = MQConstant.BUSINESS_EVENT_GROUP, topic = MQConstant.BUSINESS_EVENT_TOPIC,
        consumeThreadNumber = 2, consumeThreadMax = 4)
public class BusinessEventAuditConsumer implements RocketMQListener<MQBusinessEvent> {
    private static final long DEDUPE_DAYS = 30;

    private final AuditLogDao auditLogDao;

    @Override
    public void onMessage(MQBusinessEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("ignore malformed business event");
            return;
        }
        String dedupeKey = RedisKey.getKey("mq:event:processed:%s", event.getEventId());
        Boolean firstDelivery = RedisUtils.setIfAbsent(dedupeKey, "1", DEDUPE_DAYS, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(firstDelivery)) {
            return;
        }
        if (auditLogDao.getByEventId(event.getEventId()) != null) {
            return;
        }
        AuditLog logRecord = new AuditLog();
        logRecord.setEventId(event.getEventId());
        logRecord.setEventType(event.getEventType());
        logRecord.setAggregateId(event.getAggregateId());
        logRecord.setOperatorUid(event.getOperatorUid());
        logRecord.setPayload(event.getPayload());
        logRecord.setOccurredAt(event.getOccurredAt());
        logRecord.setCreateTime(new Date());
        try {
            auditLogDao.save(logRecord);
        } catch (Exception e) {
            // A concurrent consumer can win the unique event_id constraint. Let other errors retry.
            if (auditLogDao.getByEventId(event.getEventId()) == null) {
                throw e;
            }
            log.debug("duplicate business event ignored, eventId={}", event.getEventId());
        }
    }
}
