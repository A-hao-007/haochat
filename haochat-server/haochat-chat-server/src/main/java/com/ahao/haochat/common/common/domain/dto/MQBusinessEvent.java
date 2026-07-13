package com.ahao.haochat.common.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Versioned event envelope shared by producers and consumers.
 * The payload is JSON so adding fields to a business event does not change the MQ contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MQBusinessEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private String aggregateId;
    private Long operatorUid;
    private String payload;
    private Date occurredAt;
    private Integer schemaVersion;
}
