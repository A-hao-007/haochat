package com.ahao.haochat.common.chatai.memory;

import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chatai.dao.AiMemoryDao;
import com.ahao.haochat.common.chatai.domain.entity.AiMemory;
import com.ahao.haochat.common.chatai.handler.ChatAIHandlerFactory;
import com.ahao.haochat.common.chatai.properties.AiMemoryProperties;
import com.ahao.haochat.common.chatai.service.AiModelGateway;
import com.ahao.haochat.common.chatai.service.AiPromptService;
import com.ahao.haochat.common.common.config.ThreadPoolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Extracts only durable facts asynchronously; raw chat transcripts are not embedded wholesale. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMemoryExtractionService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_EXTRACT_PROMPT = "Extract durable chat memory from the message below. "
            + "Ignore greetings and transient chatter. Return strict JSON only: "
            + "{\"remember\":true|false,\"scope\":\"USER\"|\"ROOM\",\"kind\":\"PREFERENCE\"|\"FACT\"|\"DECISION\"|\"TASK\"|\"SUMMARY\","
            + "\"content\":\"concise factual memory\",\"importance\":1-5,\"entities\":[\"name\"]}. Message: {{message}}";

    private final AiMemoryProperties properties;
    private final AiMemoryDao memoryDao;
    private final AiModelGateway modelGateway;
    private final AiPromptService promptService;
    private final QdrantMemoryClient qdrantMemoryClient;
    private final Neo4jMemoryGraphClient graphClient;

    @Async(ThreadPoolConfig.AICHAT_EXECUTOR)
    public void capture(Message message) {
        if (!properties.isEnabled() || !eligible(message) || alreadyCaptured(message.getId())) {
            return;
        }
        try {
            Candidate candidate = extract(message.getContent());
            if (!candidate.remember || StringUtils.isBlank(candidate.content)) {
                return;
            }
            AiMemory memory = AiMemory.builder()
                    .uid(message.getFromUid())
                    .roomId("ROOM".equals(candidate.scope) ? message.getRoomId() : null)
                    .sourceMsgId(message.getId())
                    .scope(candidate.scope)
                    .kind(candidate.kind)
                    .content(StringUtils.abbreviate(candidate.content.trim(), 2000))
                    .importance(candidate.importance)
                    .status(1)
                    .createTime(new Date())
                    .build();
            memoryDao.save(memory);
            List<Float> vector = modelGateway.embed(memory.getContent(), properties.getEmbeddingModel());
            qdrantMemoryClient.upsert(memory, vector);
            graphClient.upsert(memory, candidate.entities);
        } catch (Exception e) {
            log.warn("AI memory extraction failed msgId={}", message.getId(), e);
        }
    }

    private boolean eligible(Message message) {
        if (message == null || message.getId() == null || message.getFromUid() == null
                || !MessageTypeEnum.TEXT.getType().equals(message.getType())
                || ChatAIHandlerFactory.getAllAIUserIds().contains(message.getFromUid())
                || StringUtils.isBlank(message.getContent())) {
            return false;
        }
        String content = message.getContent();
        return content.length() >= 30 || content.contains("记住") || content.contains("喜欢")
                || content.contains("决定") || content.contains("计划") || content.contains("提醒");
    }

    private boolean alreadyCaptured(Long messageId) {
        return memoryDao.lambdaQuery().eq(AiMemory::getSourceMsgId, messageId).count() > 0;
    }

    private Candidate extract(String content) throws Exception {
        String prompt = promptService.render(AiPromptService.MEMORY_EXTRACT, DEFAULT_EXTRACT_PROMPT,
                Map.of("message", content));
        String raw = modelGateway.complete(prompt, content);
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Candidate.skip();
        }
        JsonNode node = MAPPER.readTree(raw.substring(start, end + 1));
        if (!node.path("remember").asBoolean(false)) {
            return Candidate.skip();
        }
        String scope = "ROOM".equalsIgnoreCase(node.path("scope").asText()) ? "ROOM" : "USER";
        String kind = node.path("kind").asText("FACT").toUpperCase();
        if (!List.of("PREFERENCE", "FACT", "DECISION", "TASK", "SUMMARY").contains(kind)) {
            kind = "FACT";
        }
        List<String> entities = new ArrayList<>();
        for (JsonNode entity : node.path("entities")) {
            String name = StringUtils.abbreviate(entity.asText().trim(), 64);
            if (StringUtils.isNotBlank(name)) {
                entities.add(name);
            }
        }
        int importance = Math.max(1, Math.min(5, node.path("importance").asInt(1)));
        return new Candidate(true, scope, kind, node.path("content").asText(), importance, entities);
    }

    private record Candidate(boolean remember, String scope, String kind, String content, int importance,
                             List<String> entities) {
        static Candidate skip() {
            return new Candidate(false, "USER", "FACT", "", 1, List.of());
        }
    }
}
