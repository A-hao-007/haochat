package com.ahao.haochat.common.chatai.mcp;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 MCP 工具注册中心：Spring 启动时收集所有 {@link McpTool} Bean，
 * 既供 MCP Server 对外暴露，也供 Agent 进程内调用。
 */
@Component
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolRegistry(List<McpTool> toolList) {
        for (McpTool t : toolList) {
            tools.put(t.name(), t);
        }
    }

    public Collection<McpTool> all() {
        return tools.values();
    }

    public McpTool get(String name) {
        return tools.get(name);
    }
}
