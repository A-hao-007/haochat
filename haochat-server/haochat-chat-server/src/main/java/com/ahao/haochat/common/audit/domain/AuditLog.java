package com.ahao.haochat.common.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("audit_log")
public class AuditLog implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("event_id")
    private String eventId;
    @TableField("event_type")
    private String eventType;
    @TableField("aggregate_id")
    private String aggregateId;
    @TableField("operator_uid")
    private Long operatorUid;
    @TableField("payload")
    private String payload;
    @TableField("occurred_at")
    private Date occurredAt;
    @TableField("create_time")
    private Date createTime;
}
