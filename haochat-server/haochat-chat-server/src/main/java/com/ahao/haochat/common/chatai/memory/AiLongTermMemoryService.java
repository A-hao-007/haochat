package com.ahao.haochat.common.chatai.memory;

import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
import com.ahao.haochat.common.chatai.service.AiModelGateway;
import com.ahao.haochat.common.chatai.service.AiPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the retrieval augmentation without exposing memories outside their owner or room. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLongTermMemoryService {
    private static final String DEFAULT_REWRITE_PROMPT = "Rewrite the following user query for semantic retrieval. "
            + "Keep names, time expressions and intent. Return only the rewritten query. Query: {{query}}";

    private final AiMemoryProperties properties;
    private final AiModelGateway modelGateway;
    private final AiPromptService promptService;
    private final QdrantMemoryClient qdrantMemoryClient;
    private final Neo4jMemoryGraphClient graphClient;

    public String enrichUserMessage(Long roomId, Long uid, String original) {
        if (!properties.isEnabled() || StringUtils.isBlank(original)) {
            return original;
        }
        String retrievalQuery = rewrite(original);
        try {
            List<Float> vector = modelGateway.embed(retrievalQuery, properties.getEmbeddingModel());
            Set<String> context = new LinkedHashSet<>();
            addHits(context, qdrantMemoryClient.searchByUid(uid, vector, properties.getTopK()));
            addHits(context, qdrantMemoryClient.searchByRoom(roomId, vector, properties.getTopK()));
            context.addAll(graphClient.search(uid, roomId, Math.max(1, properties.getTopK() / 2)));
            if (context.isEmpty()) {
                return original;
            }
            String joined = String.join("\n- ", context);
            if (joined.length() > 1800) {
                joined = joined.substring(0, 1800);
            }
            return "[[RETRIEVED_MEMORY]]\nRelevant private or current-room memory. Use it only when relevant; "
                    + "never claim it came from the current user message:\n- " + joined
                    + "\n[[/RETRIEVED_MEMORY]]\n[[USER_MESSAGE]]" + original;
        } catch (Exception e) {
            log.warn("Long-term memory retrieval skipped roomId={} uid={}", roomId, uid, e);
            return original;
        }
    }

    private void addHits(Set<String> context, List<QdrantMemoryClient.MemoryHit> hits) {
        for (QdrantMemoryClient.MemoryHit hit : hits) {
            if (hit.score() >= 0.35D) {
                context.add("[" + hit.kind() + "] " + hit.content());
            }
        }
    }

    private String rewrite(String original) {
        if (!properties.isQueryRewriteEnabled()) {
            return original;
        }
        try {
            String prompt = promptService.render(AiPromptService.QUERY_REWRITE, DEFAULT_REWRITE_PROMPT,
                    Map.of("query", original));
            String rewritten = modelGateway.complete(prompt, original);
            return StringUtils.isBlank(rewritten) || rewritten.length() > 500 ? original : rewritten;
        } catch (Exception e) {
            return original;
        }
    }
}
