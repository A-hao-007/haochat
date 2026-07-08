package com.ahao.haochat.common.chatai.utils;

import com.ahao.haochat.common.chatai.domain.ChatGPTMsg;
import com.ahao.haochat.common.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeepSeekUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String url;
    private String model = "deepseek-chat";
    private final Map<String, String> headers;
    private int timeout = 60;
    private int maxTokens = 2048;
    private List<ChatGPTMsg> messages;
    // Agent function-calling 用：原始 message 列表（含 tool 角色）与工具定义
    private List<Map<String, Object>> rawMessages;
    private List<Map<String, Object>> tools;

    private DeepSeekUtils(String key, String url) {
        this.url = url;
        this.headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("Content-Type", "application/json");
    }

    public static DeepSeekUtils create(String key, String url) {
        if (StringUtils.isBlank(key)) throw new BusinessException("DeepSeek API key is blank");
        return new DeepSeekUtils(key, url);
    }

    public DeepSeekUtils model(String model) { this.model = model; return this; }
    public DeepSeekUtils timeout(int timeout) { this.timeout = timeout; return this; }
    public DeepSeekUtils maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
    public DeepSeekUtils message(List<ChatGPTMsg> messages) { this.messages = messages; return this; }
    public DeepSeekUtils rawMessages(List<Map<String, Object>> rawMessages) { this.rawMessages = rawMessages; return this; }
    public DeepSeekUtils tools(List<Map<String, Object>> tools) { this.tools = tools; return this; }

    /**
     * Agent function-calling：发送对话（可带 tools），返回 choices[0].message 节点（可能含 tool_calls）。
     */
    @SneakyThrows
    public JsonNode completion() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", rawMessages != null ? rawMessages : messages);
        body.put("max_tokens", maxTokens);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .post(RequestBody.create(MAPPER.writeValueAsString(body), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("DeepSeek completion error: {} {}", response.code(), response.message());
                return null;
            }
            JsonNode root = MAPPER.readTree(response.body().string());
            return root.path("choices").get(0).path("message");
        } catch (IOException e) {
            log.warn("DeepSeek completion failed: {}", e.getMessage());
            return null;
        }
    }

    @SneakyThrows
    public String sendAndGet() {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .post(RequestBody.create(MAPPER.writeValueAsString(body), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("DeepSeek API error: {} {}", response.code(), response.message());
                return null;
            }
            String json = response.body().string();
            JsonNode root = MAPPER.readTree(json);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (IOException e) {
            log.warn("DeepSeek call failed: {}", e.getMessage());
            return null;
        }
    }
}
