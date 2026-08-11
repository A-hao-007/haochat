package com.ahao.haochat.common.chatai.agent.runtime;

/** Stable step types used by tracing, diagnostics and offline evaluation. */
public interface AgentStepType {
    String RETRIEVAL = "RETRIEVAL";
    String MODEL = "MODEL";
    String TOOL = "TOOL";
    String CONFIRMATION = "CONFIRMATION";
}
