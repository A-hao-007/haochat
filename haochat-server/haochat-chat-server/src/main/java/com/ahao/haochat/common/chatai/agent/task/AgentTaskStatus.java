package com.ahao.haochat.common.chatai.agent.task;

/**
 * agent_task.status 取值
 */
public interface AgentTaskStatus {
    int PENDING = 0;
    int RUNNING = 1;
    int WAITING = 2;
    int DONE = 3;
    int CANCELLED = 4;
    int FAILED = 5;
}
