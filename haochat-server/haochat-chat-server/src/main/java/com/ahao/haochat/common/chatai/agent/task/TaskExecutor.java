package com.ahao.haochat.common.chatai.agent.task;

/**
 * Agent 任务状态机的具体任务处理器，按 taskType 分发。
 * 执行成功/失败后的状态流转由具体实现自己负责（一次性任务转 DONE，周期任务重算 next_run_time 保持 PENDING），
 * {@link com.ahao.haochat.common.chatai.agent.AgentTaskScheduler} 只负责分发和捕获未处理的异常。
 */
public interface TaskExecutor {

    /** 对应 agent_task.task_type，如 "REMINDER" */
    String taskType();

    void execute(AgentTask task);
}
