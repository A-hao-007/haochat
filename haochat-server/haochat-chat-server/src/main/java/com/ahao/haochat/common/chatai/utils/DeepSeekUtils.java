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
