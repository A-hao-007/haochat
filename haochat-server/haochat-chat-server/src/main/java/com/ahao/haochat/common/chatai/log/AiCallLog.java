package com.ahao.haochat.common.chatai.log;

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

/**
 * <p>
 * AI Agent 调用日志
 * </p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_call_log")
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("uid")
    private Long uid;

    @TableField("room_id")
    private Long roomId;

    @TableField("user_input")
    private String userInput;

    @TableField("final_reply")
    private String finalReply;

    /** 本次调用涉及的工具序列，JSON 数组文本 */
    @TableField("tool_calls")
    private String toolCalls;

    @TableField("turn_count")
    private Integer turnCount;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("success")
    private Integer success;

    @TableField("error_msg")
    private String errorMsg;

    /** 本次是否触发了需要用户确认的动作 */
    @TableField("needs_confirmation")
    private Integer needsConfirmation;

    /** 用户是否确认执行（仅 needsConfirmation=1 时有意义） */
    @TableField("confirmed")
    private Integer confirmed;

    @TableField("create_time")
    private Date createTime;
}
