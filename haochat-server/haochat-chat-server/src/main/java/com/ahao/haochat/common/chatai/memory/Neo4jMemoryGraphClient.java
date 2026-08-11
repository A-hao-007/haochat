package com.ahao.haochat.common.chatai.memory;

import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
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

/** Minimal Cypher-over-HTTP client for durable memory relationships. */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jMemoryGraphClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AiMemoryProperties properties;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public void upsert(AiMemory memory, List<String> entities) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI memory is disabled");
        }
        if (StringUtils.isBlank(properties.getNeo4jPassword())) {
            throw new IllegalStateException("Neo4j password is not configured");
        }
        String cypher = "MERGE (u:AiUser {uid:$uid}) "
                + "MERGE (m:AiMemory {memoryId:$memoryId}) "
                + "SET m.ownerUid=$uid, m.roomId=$roomId, m.scope=$scope, m.kind=$kind, "
                + "m.content=$content, m.importance=$importance, m.status=1 "
                + "MERGE (u)-[:OWNS]->(m) "
                + "WITH m OPTIONAL MATCH (m)-[old:MENTIONS]->(:AiEntity) DELETE old "
                + "WITH DISTINCT m UNWIND $entities AS entity "
                + "MERGE (e:AiEntity {name:entity}) MERGE (m)-[:MENTIONS]->(e)";
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("uid", memory.getUid());
            params.put("memoryId", memory.getId());
            params.put("roomId", memory.getRoomId());
            params.put("scope", memory.getScope());
            params.put("kind", memory.getKind());
            params.put("content", memory.getContent());
            params.put("importance", memory.getImportance());
            params.put("entities", entities);
            execute(cypher, params);
        } catch (Exception e) {
            log.warn("Neo4j memory upsert failed id={}", memory.getId(), e);
            throw new IllegalStateException("Neo4j memory upsert failed", e);
        }
    }

    public List<String> search(Long uid, Long roomId, String query, int limit) {
        if (!properties.isEnabled() || StringUtils.isBlank(properties.getNeo4jPassword())) {
            return List.of();
        }
        String cypher = "MATCH (owner:AiUser)-[:OWNS]->(seed:AiMemory)-[:MENTIONS]->(entity:AiEntity) "
                + "WHERE seed.status = 1 AND (owner.uid = $uid OR seed.roomId = $roomId) "
                + "AND size(entity.name) >= 2 AND toLower($query) CONTAINS toLower(entity.name) "
                + "WITH DISTINCT entity "
                + "MATCH (relatedOwner:AiUser)-[:OWNS]->(memory:AiMemory)-[:MENTIONS]->(entity) "
                + "WHERE memory.status = 1 AND (relatedOwner.uid = $uid OR memory.roomId = $roomId) "
                + "RETURN DISTINCT memory.content AS content, memory.importance AS importance, entity.name AS entity "
                + "ORDER BY importance DESC LIMIT $limit";
        try {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("uid", uid);
            parameters.put("roomId", roomId);
            parameters.put("query", StringUtils.defaultString(query));
            parameters.put("limit", limit);
            JsonNode response = execute(cypher, parameters);
            List<String> facts = new ArrayList<>();
            for (JsonNode row : response.path("results").path(0).path("data")) {
                String content = row.path("row").path(0).asText();
                if (StringUtils.isNotBlank(content)) {
                    String entity = row.path("row").path(2).asText();
                    facts.add(StringUtils.isBlank(entity) ? content : "[关联实体:" + entity + "] " + content);
                }
            }
            return facts;
        } catch (Exception e) {
            log.warn("Neo4j memory search failed uid={} roomId={}", uid, roomId, e);
            return List.of();
        }
    }

    public boolean health() {
        if (StringUtils.isBlank(properties.getNeo4jPassword())) {
            return false;
        }
        try {
            execute("RETURN 1 AS ok", Map.of());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode execute(String statement, Map<String, Object> parameters) throws IOException {
        Map<String, Object> body = Map.of("statements", List.of(Map.of("statement", statement, "parameters", parameters)));
        Request request = new Request.Builder()
                .url(StringUtils.removeEnd(properties.getNeo4jUrl(), "/") + "/db/neo4j/tx/commit")
                .header("Authorization", Credentials.basic(properties.getNeo4jUsername(), properties.getNeo4jPassword()))
                .post(RequestBody.create(MAPPER.writeValueAsString(body), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String content = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Neo4j HTTP " + response.code());
            }
            JsonNode parsed = MAPPER.readTree(content);
            if (parsed.path("errors").isArray() && parsed.path("errors").size() > 0) {
                throw new IOException("Neo4j query failed");
            }
            return parsed;
        }
    }
}
