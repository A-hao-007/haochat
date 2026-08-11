package com.ahao.haochat.common.chatai.agent.runtime;

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

/** Durable state for one Agent request. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_run")
public class AgentRun implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("run_id")
    private String runId;
    @TableField("request_id")
    private String requestId;
    @TableField("uid")
    private Long uid;
    @TableField("room_id")
    private Long roomId;
    @TableField("status")
    private String status;
    @TableField("current_step")
    private String currentStep;
    @TableField("provider")
    private String provider;
    @TableField("model")
    private String model;
    @TableField("user_input")
    private String userInput;
    @TableField("final_reply")
    private String finalReply;
    @TableField("tool_call_count")
    private Integer toolCallCount;
    @TableField("first_token_latency_ms")
    private Integer firstTokenLatencyMs;
    @TableField("latency_ms")
    private Integer latencyMs;
    @TableField("error_code")
    private String errorCode;
    @TableField("error_message")
    private String errorMessage;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
    @TableField("finish_time")
    private Date finishTime;
}
