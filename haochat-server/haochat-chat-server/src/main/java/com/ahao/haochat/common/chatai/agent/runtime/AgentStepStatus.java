package com.ahao.haochat.common.chatai.agent.runtime;

/** Lifecycle states for an Agent execution step. */
public interface AgentStepStatus {
    String RUNNING = "RUNNING";
    String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    String SUCCEEDED = "SUCCEEDED";
    String FAILED = "FAILED";
    String CANCELLED = "CANCELLED";
}
