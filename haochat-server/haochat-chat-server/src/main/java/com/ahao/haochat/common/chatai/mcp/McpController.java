package com.ahao.haochat.common.chatai.mcp;

import com.ahao.haochat.common.common.utils.RequestHolder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server：JSON-RPC 2.0 over HTTP，暴露 HaoChat 自有工具，
 * 供本地 Agent 以及外部 MCP 客户端（如 Claude Desktop）调用。
 * 走 /capi 鉴权，调用者 uid 由鉴权上下文注入。
 */
@Slf4j
@Api(tags = "MCP 工具服务")
@RestController
@RequestMapping("/capi/mcp")
public class McpController {

    @Autowired
    private McpToolRegistry registry;

    @PostMapping
    @ApiOperation("MCP JSON-RPC 入口")
    public Map<String, Object> handle(@RequestBody Map<String, Object> req) {
        Object id = req.get("id");
        String method = String.valueOf(req.get("method"));
        try {
            switch (method) {
                case "initialize":
                    return ok(id, initResult());
                case "tools/list":
                    return ok(id, Collections.singletonMap("tools", listTools()));
                case "tools/call":
                    return ok(id, callTool(req));
                case "ping":
                    return ok(id, Collections.emptyMap());
                default:
                    return err(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.warn("MCP handle error, method={}", method, e);
            return err(id, -32603, String.valueOf(e.getMessage()));
        }
    }

    private Map<String, Object> initResult() {
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "haochat-mcp");
        serverInfo.put("version", "1.0.0");
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", Collections.singletonMap("tools", Collections.emptyMap()));
        result.put("serverInfo", serverInfo);
        return result;
    }

    private List<Map<String, Object>> listTools() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (McpTool t : registry.all()) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("inputSchema", t.inputSchema());
            list.add(m);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(Map<String, Object> req) {
        Map<String, Object> params = (Map<String, Object>) req.get("params");
        String name = String.valueOf(params.get("name"));
        Map<String, Object> args = params.get("arguments") == null
                ? new HashMap<>() : new HashMap<>((Map<String, Object>) params.get("arguments"));
        McpTool tool = registry.get(name);
        if (tool == null) {
            return content("未知工具: " + name, true);
        }
        // 外部调用：用鉴权上下文注入调用者
        args.putIfAbsent("_callerUid", RequestHolder.get().getUid());
        try {
            return content(tool.execute(args), false);
        } catch (Exception e) {
            log.warn("MCP tool {} execute error", name, e);
            return content("工具执行失败: " + e.getMessage(), true);
        }
    }

    private Map<String, Object> content(String text, boolean isError) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", "text");
        item.put("text", text);
        Map<String, Object> result = new HashMap<>();
        result.put("content", Collections.singletonList(item));
        result.put("isError", isError);
        return result;
    }

    private Map<String, Object> ok(Object id, Object result) {
        Map<String, Object> r = new HashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", result);
        return r;
    }

    private Map<String, Object> err(Object id, int code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> r = new HashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("error", error);
        return r;
    }
}
