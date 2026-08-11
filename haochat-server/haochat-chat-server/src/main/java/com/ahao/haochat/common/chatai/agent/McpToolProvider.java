package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.chatai.agent.runtime.AgentRunTraceService;
import com.ahao.haochat.common.chatai.agent.runtime.AgentStepStatus;
import com.ahao.haochat.common.chatai.agent.runtime.AgentStepType;
import com.ahao.haochat.common.chatai.agent.runtime.AgentToolExecutionService;
import com.ahao.haochat.common.chatai.mcp.McpClient;
import com.ahao.haochat.common.chatai.mcp.McpTool;
import com.ahao.haochat.common.chatai.mcp.McpToolRegistry;
import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 桥接现有 McpToolRegistry（本地工具）+ McpClient（外部MCP工具）到 LangChain4j 的 ToolProvider 协议。
 * 新增工具仍然只需要写一个 {@link McpTool} 实现类，不需要碰这里的代码。
 * _callerUid/_roomId 隐藏参数注入逻辑原样保留：从 memoryId（"roomId:uid"）解析回填，不暴露给模型。
 */
@Slf4j
@Component
public class McpToolProvider implements ToolProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private McpToolRegistry registry;
    @Autowired
    private McpClient mcpClient;
    @Autowired
    private PendingConfirmStore pendingConfirmStore;
    @Autowired
    private AgentRunTraceService traceService;
    @Autowired
    private AgentToolExecutionService executionService;

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        for (McpTool tool : registry.all()) {
            tools.put(toSpec(tool.name(), tool.description(), tool.inputSchema()), localExecutor(tool));
        }
        for (Map<String, Object> def : mcpClient.remoteToolDefs()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) def.get("function");
            String name = String.valueOf(fn.get("name"));
            String description = String.valueOf(fn.getOrDefault("description", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) fn.get("parameters");
            tools.put(toSpec(name, description, schema), remoteExecutor(name));
        }
        return new ToolProviderResult(tools);
    }

    private ToolExecutor localExecutor(McpTool tool) {
        return (ToolExecutionRequest execRequest, Object memoryId) -> {
            String runId = parseRunId(memoryId);
            long started = System.currentTimeMillis();
            String stepId = traceService.beginStep(runId, AgentStepType.TOOL, tool.name(), execRequest.arguments());
            traceService.incrementToolCalls(runId);
            Map<String, Object> rawArgs = parseArgs(execRequest.arguments());
            if (tool.needsConfirmation()) {
                String result = proposeForConfirmation(tool, rawArgs, memoryId, execRequest.arguments());
                traceService.finishStep(stepId, AgentStepStatus.WAITING_CONFIRMATION, result, 1,
                        elapsed(started), null, null);
                return result;
            }
            Map<String, Object> args = new HashMap<>(rawArgs);
            injectContext(args, memoryId);
            if (!tool.isReadOnly() && !claimToolCall(runId, execRequest.id())) {
                String duplicate = "该工具调用已经处理过，不会重复执行";
                traceService.finishStep(stepId, AgentStepStatus.SUCCEEDED, duplicate, 1,
                        elapsed(started), "DUPLICATE_SUPPRESSED", null);
                return duplicate;
            }
            AgentToolExecutionService.Result result = executionService.executeLocal(tool, args);
            traceService.finishStep(stepId,
                    result.isSuccess() ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                    result.getOutput(), result.getAttempts(), result.getLatencyMs(),
                    result.getErrorCode(), result.getErrorMessage());
            return result.userText();
        };
    }

    /**
     * 高风险写操作：不立即执行，把方案写入 Redis 待确认，返回一段说明文案交给模型转述给用户。
     * 下一次用户在同一会话发消息时，由 AgentService 在进入正式 Agent 循环前拦截判断确认/取消。
     */
    private String proposeForConfirmation(McpTool tool, Map<String, Object> rawArgs, Object memoryId, String argsJson) {
        long[] roomIdUid = parseMemoryId(memoryId);
        if (roomIdUid == null) {
            return "缺少会话上下文，无法发起确认流程";
        }
        String argsDisplay = rawArgs.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .reduce((a, b) -> a + "，" + b).orElse("");
        String summary = tool.description() + "（" + argsDisplay + "）";
        pendingConfirmStore.save(roomIdUid[0], roomIdUid[1],
                new PendingConfirmation(parseRunId(memoryId), tool.name(), argsJson, summary));
        return "【需要你确认】即将执行：" + summary + "。请回复\"确认\"执行，或回复\"取消\"放弃（5分钟内有效）。";
    }

    private ToolExecutor remoteExecutor(String name) {
        return (ToolExecutionRequest execRequest, Object memoryId) -> {
            String runId = parseRunId(memoryId);
            String stepId = traceService.beginStep(runId, AgentStepType.TOOL, name, execRequest.arguments());
            traceService.incrementToolCalls(runId);
            Map<String, Object> args = parseArgs(execRequest.arguments());
            injectContext(args, memoryId);
            AgentToolExecutionService.Result result = executionService.executeRemote(name,
                    () -> mcpClient.callRemote(name, args));
            traceService.finishStep(stepId,
                    result.isSuccess() ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                    result.getOutput(), result.getAttempts(), result.getLatencyMs(),
                    result.getErrorCode(), result.getErrorMessage());
            return result.userText();
        };
    }

    private Map<String, Object> parseArgs(String argsJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = MAPPER.readValue(argsJson, Map.class);
            return args;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /** memoryId 格式为 "roomId:uid:runId"，解析失败时工具自身会报"缺少会话上下文"。 */
    private void injectContext(Map<String, Object> args, Object memoryId) {
        long[] roomIdUid = parseMemoryId(memoryId);
        if (roomIdUid != null) {
            args.put("_roomId", roomIdUid[0]);
            args.put("_callerUid", roomIdUid[1]);
        }
    }

    private long[] parseMemoryId(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        String[] parts = String.valueOf(memoryId).split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseRunId(Object memoryId) {
        if (memoryId == null) return null;
        String[] parts = String.valueOf(memoryId).split(":", 3);
        return parts.length == 3 ? parts[2] : null;
    }

    private boolean claimToolCall(String runId, String toolCallId) {
        if (runId == null || toolCallId == null || toolCallId.isBlank()) {
            return true;
        }
        Boolean created = RedisUtils.setIfAbsent(
                RedisKey.join("agent", "tool", "idempotency", runId, toolCallId),
                "1", 1, TimeUnit.DAYS);
        // Redis degradation should not make the tool permanently unavailable; the durable
        // request-id guard still prevents duplicate Agent runs for the same chat message.
        return created == null || created;
    }

    private int elapsed(long started) {
        long value = System.currentTimeMillis() - started;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /** 把现有 McpTool.inputSchema() 的原始 JSON Schema（Map）转换成 LangChain4j 的 JsonObjectSchema */
    @SuppressWarnings("unchecked")
    private ToolSpecification toSpec(String name, String description, Map<String, Object> rawSchema) {
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        if (rawSchema != null) {
            Object propsObj = rawSchema.get("properties");
            if (propsObj instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) propsObj).entrySet()) {
                    String propName = entry.getKey();
                    Map<String, Object> propSchema = entry.getValue() instanceof Map
                            ? (Map<String, Object>) entry.getValue() : Map.of();
                    String propType = String.valueOf(propSchema.getOrDefault("type", "string"));
                    String propDesc = String.valueOf(propSchema.getOrDefault("description", ""));
                    switch (propType) {
                        case "integer":
                            schemaBuilder.addIntegerProperty(propName, propDesc);
                            break;
                        case "number":
                            schemaBuilder.addNumberProperty(propName, propDesc);
                            break;
                        case "boolean":
                            schemaBuilder.addBooleanProperty(propName, propDesc);
                            break;
                        default:
                            schemaBuilder.addStringProperty(propName, propDesc);
                    }
                }
            }
            Object requiredObj = rawSchema.get("required");
            if (requiredObj instanceof List) {
                List<String> required = ((List<?>) requiredObj).stream().map(String::valueOf).toList();
                schemaBuilder.required(required);
            }
        }
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(schemaBuilder.build())
                .build();
    }
}
