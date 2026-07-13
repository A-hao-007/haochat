package com.ahao.haochat.common.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("group_operation_log")
public class GroupOperationLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("room_id")
    private Long roomId;

    @TableField("group_id")
    private Long groupId;

    @TableField("operator_uid")
    private Long operatorUid;

    @TableField("target_uid")
    private Long targetUid;

    @TableField("type")
    private Integer type;

    @TableField("content")
    private String content;

    @TableField("create_time")
    private Date createTime;
}
