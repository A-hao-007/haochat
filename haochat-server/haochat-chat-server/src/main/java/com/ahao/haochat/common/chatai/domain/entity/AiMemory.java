package com.ahao.haochat.common.chatai.domain.entity;

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
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_memory")
public class AiMemory implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("uid")
    private Long uid;
    @TableField("room_id")
    private Long roomId;
    @TableField("source_msg_id")
    private Long sourceMsgId;
    private String scope;
    private String kind;
    private String content;
    private Integer importance;
    private Integer status;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
