package com.ahao.haochat.common.chatai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP Client：连接外部 MCP Server（JSON-RPC over HTTP），把其工具汇入 Agent。
 * 通过 haochat.mcp.external-servers 配置（逗号分隔 URL），留空则不启用。
 */
@Slf4j
@Component
public class McpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final long TOOL_DEFS_CACHE_MS = 5 * 60 * 1000L;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    @Value("${haochat.mcp.external-servers:}")
    private String externalServers;

    /** 工具名 -> 所属服务器 URL */
    private final Map<String, String> toolServer = new ConcurrentHashMap<>();
    /** remoteToolDefs() 结果缓存，避免每轮对话都同步请求外部 server 的 tools/list */
    private volatile List<Map<String, Object>> cachedToolDefs;
    private volatile long cachedAt;

    private List<String> servers() {
        List<String> list = new ArrayList<>();
        if (externalServers == null || externalServers.trim().isEmpty()) {
            return list;
        }
        for (String s : externalServers.split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    /** 拉取所有外部工具的 OpenAI function 定义（5分钟TTL缓存，避免每轮对话都同步请求外部 server） */
    public List<Map<String, Object>> remoteToolDefs() {
        List<Map<String, Object>> cached = cachedToolDefs;
        if (cached != null && System.currentTimeMillis() - cachedAt < TOOL_DEFS_CACHE_MS) {
            return cached;
        }
        List<Map<String, Object>> defs = fetchRemoteToolDefs();
        cachedToolDefs = defs;
        cachedAt = System.currentTimeMillis();
        return defs;
    }

    private List<Map<String, Object>> fetchRemoteToolDefs() {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (String server : servers()) {
            try {
                JsonNode result = rpc(server, "tools/list", new HashMap<>());
                if (result == null) {
                    continue;
                }
                for (JsonNode t : result.path("tools")) {
                    String name = t.path("name").asText();
                    toolServer.put(name, server);
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", name);
                    fn.put("description", t.path("description").asText(""));
                    fn.put("parameters", MAPPER.convertValue(t.path("inputSchema"), Map.class));
                    Map<String, Object> def = new LinkedHashMap<>();
                    def.put("type", "function");
                    def.put("function", fn);
                    defs.add(def);
                }
            } catch (Exception e) {
                log.warn("MCP external tools/list failed: {}", server, e);
            }
        }
        return defs;
    }

    public boolean hasTool(String name) {
        return toolServer.containsKey(name);
    }

    /** 调用外部工具，返回文本结果 */
    public String callRemote(String name, Map<String, Object> args) {
        String server = toolServer.get(name);
        if (server == null) {
            return "未找到外部工具: " + name;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("arguments", args);
        JsonNode result = rpc(server, "tools/call", params);
        if (result == null) {
            return "外部工具调用失败";
        }
        JsonNode content = result.path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText("");
        }
        return "";
    }

    private JsonNode rpc(String server, String method, Map<String, Object> params) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("jsonrpc", "2.0");
            req.put("id", System.currentTimeMillis());
            req.put("method", method);
            req.put("params", params);
            Request request = new Request.Builder()
                    .url(server)
                    .post(RequestBody.create(MAPPER.writeValueAsString(req), JSON))
                    .build();
            try (Response resp = CLIENT.newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    return null;
                }
                return MAPPER.readTree(resp.body().string()).path("result");
            }
        } catch (Exception e) {
            log.warn("MCP rpc {} {} failed", server, method, e);
            return null;
        }
    }
}
