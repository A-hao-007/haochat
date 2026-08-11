package com.ahao.haochat.common.chatai.agent.runtime;

/** Lifecycle states for one durable Agent execution. */
public interface AgentRunStatus {
    String RUNNING = "RUNNING";
    String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    String SUCCEEDED = "SUCCEEDED";
    String FAILED = "FAILED";
    String CANCELLED = "CANCELLED";
}
