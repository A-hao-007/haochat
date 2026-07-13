package com.ahao.haochat.common.common.service;

import com.ahao.haochat.common.common.constant.MQConstant;
import com.ahao.haochat.common.common.domain.dto.MQBusinessEvent;
import com.ahao.haochat.transaction.service.MQProducer;
import com.ahao.haochat.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

/** Publishes a business event through the transactional local-message mechanism. */
@Component
@RequiredArgsConstructor
public class BusinessEventPublisher {
    private final MQProducer mqProducer;

    public void publish(String eventType, String aggregateId, Long operatorUid, Object payload) {
        String payloadJson = JsonUtils.toStr(payload);
        MQBusinessEvent event = MQBusinessEvent.builder()
                .eventId(deterministicId(eventType, aggregateId, payloadJson))
                .eventType(eventType)
                .aggregateId(aggregateId)
                .operatorUid(operatorUid)
                .payload(payloadJson)
                .occurredAt(new Date())
                .schemaVersion(1)
                .build();
        mqProducer.sendSecureMsg(MQConstant.BUSINESS_EVENT_TOPIC, event, event.getEventId());
    }

    private String deterministicId(String eventType, String aggregateId, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((eventType + "|" + aggregateId + "|" + payload)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("cannot build MQ event id", e);
        }
    }
}
