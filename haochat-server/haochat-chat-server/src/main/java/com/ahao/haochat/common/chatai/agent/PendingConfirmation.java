package com.ahao.haochat.common.chatai.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待用户确认的高风险工具调用（写Redis，TTL 5分钟）。
 */
@Data
@NoArgsConstructor
public class PendingConfirmation {
    /** Agent run that proposed the operation, used to resume the durable state machine. */
    private String runId;
    private String toolName;
    /** 工具入参的 JSON 文本（不含 _callerUid/_roomId，执行时重新注入） */
    private String argsJson;
    /** 展示给用户看的方案摘要文案 */
    private String summary;

    public PendingConfirmation(String toolName, String argsJson, String summary) {
        this(null, toolName, argsJson, summary);
    }

    public PendingConfirmation(String runId, String toolName, String argsJson, String summary) {
        this.runId = runId;
        this.toolName = toolName;
        this.argsJson = argsJson;
        this.summary = summary;
    }
}
