package com.ahao.haochat.common.chatai.mcp;

import java.util.Map;

/**
 * MCP 工具：被 MCP Server 暴露、被 Agent 调用。
 * execute 的 args 中会由调用方注入 _callerUid（调用者）/_roomId（会话），工具据此做权限与上下文。
 */
public interface McpTool {

    String name();

    String description();

    /** 入参的 JSON Schema（object） */
    Map<String, Object> inputSchema();

    /** 执行工具，返回纯文本结果 */
    String execute(Map<String, Object> args);

    /**
     * 是否为高风险写操作，需要用户二次确认才能真正执行。
     * 默认 false（只读工具/低风险写操作，如创建提醒——只影响自己，不需要确认）。
     * 返回 true 时，工具本身仍只需要实现"真正执行"的逻辑，确认门禁由调用编排层统一处理，
     * 工具自身不需要感知确认机制。
     */
    default boolean needsConfirmation() {
        return false;
    }
}
