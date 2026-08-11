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

/** One observable operation inside an Agent run. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_run_step")
public class AgentRunStep implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("step_id")
    private String stepId;
    @TableField("run_id")
    private String runId;
    @TableField("step_type")
    private String stepType;
    @TableField("step_name")
    private String stepName;
    @TableField("status")
    private String status;
    @TableField("input_summary")
    private String inputSummary;
    @TableField("output_summary")
    private String outputSummary;
    @TableField("attempt_count")
    private Integer attemptCount;
    @TableField("latency_ms")
    private Integer latencyMs;
    @TableField("error_code")
    private String errorCode;
    @TableField("error_message")
    private String errorMessage;
    @TableField("create_time")
    private Date createTime;
    @TableField("finish_time")
    private Date finishTime;
}
