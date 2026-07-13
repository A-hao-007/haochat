package com.ahao.haochat.common.chatai.service;

import com.ahao.haochat.common.chatai.properties.AiProviderProperties;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Small OpenAI-compatible client used by non-streaming AI infrastructure. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelGateway {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AiProviderProperties providerProperties;
    private final DeepSeekProperties deepSeekProperties;

    public String complete(String systemPrompt, String userPrompt) {
        RuntimeException failure = null;
        for (AiProviderProperties.Provider provider : providers()) {
            int attempts = Math.max(1, provider.getMaxRetries() + 1);
            for (int i = 0; i < attempts; i++) {
                try {
                    return chat(provider, systemPrompt, userPrompt);
                } catch (RuntimeException e) {
                    failure = e;
                    log.warn("AI provider request failed model={} attempt={}/{}", provider.getModel(), i + 1, attempts);
                }
            }
        }
        throw failure == null ? new IllegalStateException("No AI provider configured") : failure;
    }

    public List<Float> embed(String input, String embeddingModel) {
        RuntimeException failure = null;
        List<AiProviderProperties.Provider> embeddingProviders = configured(providerProperties.getEmbedding())
                ? List.of(providerProperties.getEmbedding()) : providers();
        for (AiProviderProperties.Provider provider : embeddingProviders) {
            try {
                String model = StringUtils.defaultIfBlank(provider.getModel(), embeddingModel);
                return embeddings(provider, input, model);
            } catch (RuntimeException e) {
                failure = e;
                log.warn("Embedding request failed model={}", provider.getModel());
            }
        }
        throw failure == null ? new IllegalStateException("No AI provider configured") : failure;
    }

    private String chat(AiProviderProperties.Provider provider, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getModel());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        body.put("temperature", 0);
        JsonNode response = post(provider, "/chat/completions", body);
        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("AI provider returned empty content");
        }
        return content.trim();
    }

    private List<Float> embeddings(AiProviderProperties.Provider provider, String input, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        JsonNode response = post(provider, "/embeddings", body);
        JsonNode vector = response.path("data").path(0).path("embedding");
        if (!vector.isArray() || vector.isEmpty()) {
            throw new IllegalStateException("AI provider returned empty embedding");
        }
        List<Float> result = new ArrayList<>(vector.size());
        for (JsonNode value : vector) {
            result.add((float) value.asDouble());
        }
        return result;
    }

    private JsonNode post(AiProviderProperties.Provider provider, String path, Map<String, Object> body) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(provider.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(provider.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        try {
            Request request = new Request.Builder()
                    .url(baseUrl(provider.getBaseUrl()) + path)
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .post(RequestBody.create(MAPPER.writeValueAsString(body), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("AI provider HTTP " + response.code());
                }
                return MAPPER.readTree(responseBody);
            }
        } catch (IOException e) {
            throw new IllegalStateException("AI provider unavailable", e);
        }
    }

    private List<AiProviderProperties.Provider> providers() {
        List<AiProviderProperties.Provider> providers = new ArrayList<>();
        AiProviderProperties.Provider primary = providerProperties.getPrimary();
        if (configured(primary)) {
            providers.add(primary);
        } else if (deepSeekProperties.isUse() && StringUtils.isNotBlank(deepSeekProperties.getKey())) {
            AiProviderProperties.Provider legacy = new AiProviderProperties.Provider();
            legacy.setEnabled(true);
            legacy.setBaseUrl(deepSeekProperties.getUrl());
            legacy.setApiKey(deepSeekProperties.getKey());
            legacy.setModel(deepSeekProperties.getModel());
            legacy.setTimeoutSeconds(deepSeekProperties.getTimeout());
            legacy.setMaxRetries(1);
            providers.add(legacy);
        }
        AiProviderProperties.Provider fallback = providerProperties.getFallback();
        if (configured(fallback)) {
            providers.add(fallback);
        }
        return providers;
    }

    private boolean configured(AiProviderProperties.Provider provider) {
        return provider != null && provider.isEnabled()
                && StringUtils.isNotBlank(provider.getBaseUrl())
                && StringUtils.isNotBlank(provider.getApiKey())
                && StringUtils.isNotBlank(provider.getModel());
    }

    private String baseUrl(String url) {
        String normalized = StringUtils.removeEnd(url.trim(), "/");
        return StringUtils.removeEnd(normalized, "/chat/completions");
    }
}
