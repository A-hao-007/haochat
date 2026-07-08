package com.ahao.haochat.common.chatai.agent.task;

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
 * Agent 任务状态机：取代原先写 Redis ZSet 的定时提醒方式，支持扩展更多任务类型。
 * </p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_task")
public class AgentTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者 */
    @TableField("uid")
    private Long uid;

    /** 投递目标会话 */
    @TableField("room_id")
    private Long roomId;

    /** 任务类型，如 REMINDER */
    @TableField("task_type")
    private String taskType;

    /** 0待执行 1执行中 2等待事件 3已完成 4已取消 5失败 */
    @TableField("status")
    private Integer status;

    /** 任务参数，按 taskType 解释，JSON文本 */
    @TableField("payload")
    private String payload;

    @TableField("next_run_time")
    private Date nextRunTime;

    @TableField("last_run_time")
    private Date lastRunTime;

    @TableField("run_count")
    private Integer runCount;

    /** 周期任务的执行次数上限，防止永久空转 */
    @TableField("max_run_count")
    private Integer maxRunCount;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
