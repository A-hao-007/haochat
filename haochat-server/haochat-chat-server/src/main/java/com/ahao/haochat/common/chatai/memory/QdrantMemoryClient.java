package com.ahao.haochat.common.chatai.memory;

import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Qdrant REST adapter. Qdrant stays optional and failures never block chat delivery. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantMemoryClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AiMemoryProperties properties;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public void upsert(AiMemory memory, List<Float> vector) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI memory is disabled");
        }
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("Embedding vector is empty");
        }
        try {
            ensureCollection(vector.size());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", memory.getUid());
            if (memory.getRoomId() != null) {
                payload.put("roomId", memory.getRoomId());
            }
            payload.put("scope", memory.getScope());
            payload.put("kind", memory.getKind());
            payload.put("content", memory.getContent());
            payload.put("importance", memory.getImportance());
            payload.put("status", memory.getStatus());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", memory.getId());
            point.put("vector", vector);
            point.put("payload", payload);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("points", List.of(point));
            request("PUT", "/collections/" + properties.getQdrantCollection() + "/points?wait=true", body);
        } catch (Exception e) {
            log.warn("Qdrant memory upsert failed id={}", memory.getId(), e);
            throw new IllegalStateException("Qdrant memory upsert failed", e);
        }
    }

    public List<MemoryHit> searchByUid(Long uid, List<Float> vector, int limit) {
        return search(Map.of("uid", uid, "scope", "USER", "status", 1), vector, limit);
    }

    public List<MemoryHit> searchByRoom(Long roomId, List<Float> vector, int limit) {
        if (roomId == null) {
            return List.of();
        }
        return search(Map.of("roomId", roomId, "scope", "ROOM", "status", 1), vector, limit);
    }

    private List<MemoryHit> search(Map<String, Object> matches, List<Float> vector, int limit) {
        if (!properties.isEnabled() || vector.isEmpty() || matches.isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> must = new ArrayList<>();
            for (Map.Entry<String, Object> entry : matches.entrySet()) {
                must.add(Map.of("key", entry.getKey(), "match", Map.of("value", entry.getValue())));
            }
            Map<String, Object> filter = Map.of("must", must);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", vector);
            body.put("limit", limit);
            body.put("with_payload", true);
            body.put("filter", filter);
            JsonNode response = request("POST", "/collections/" + properties.getQdrantCollection() + "/points/search", body);
            List<MemoryHit> result = new ArrayList<>();
            for (JsonNode point : response.path("result")) {
                JsonNode payload = point.path("payload");
                String content = payload.path("content").asText();
                if (StringUtils.isNotBlank(content)) {
                    result.add(new MemoryHit(content, payload.path("kind").asText("FACT"), point.path("score").asDouble()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Qdrant memory search failed filters={}", matches.keySet(), e);
            throw new IllegalStateException("Qdrant memory search failed", e);
        }
    }

    public boolean health() {
        Request request = new Request.Builder().url(url("/healthz")).get().build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureCollection(int dimension) throws IOException {
        String path = "/collections/" + properties.getQdrantCollection();
        Request check = new Request.Builder().url(url(path)).get().build();
        try (Response response = client.newCall(check).execute()) {
            if (response.isSuccessful()) {
                return;
            }
            if (response.code() != 404) {
                throw new IOException("Qdrant collection check failed: " + response.code());
            }
        }
        request("PUT", path, Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")));
    }

    private JsonNode request(String method, String path, Object body) throws IOException {
        RequestBody requestBody = RequestBody.create(MAPPER.writeValueAsString(body), JSON);
        Request.Builder builder = new Request.Builder().url(url(path));
        if ("PUT".equals(method)) {
            builder.put(requestBody);
        } else {
            builder.post(requestBody);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String content = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Qdrant HTTP " + response.code());
            }
            return StringUtils.isBlank(content) ? MAPPER.createObjectNode() : MAPPER.readTree(content);
        }
    }

    private String url(String path) {
        return StringUtils.removeEnd(properties.getQdrantUrl(), "/") + path;
    }

    public record MemoryHit(String content, String kind, double score) {
    }
}
