package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent 对话记忆落地 Redis，key 沿用旧版手写 Agent 循环的 "ai:context:{roomId}:{uid}" 规则
 * （memoryId 即 "{roomId}:{uid}"，由 AgentService 组装）。
 * 与旧版不同：这里完整保存工具调用消息（AiMessage.toolExecutionRequests / ToolExecutionResultMessage），
 * 不再只存问答文本，配合 TokenWindowChatMemory 按 token 数裁剪。
 */
@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TTL_MINUTES = 30;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = RedisUtils.getStr(key(memoryId));
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            ArrayNode arr = (ArrayNode) MAPPER.readTree(json);
            List<ChatMessage> messages = new ArrayList<>();
            for (JsonNode node : arr) {
                ChatMessage m = fromNode(node);
                if (m != null) {
                    messages.add(m);
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("加载AI会话上下文失败 memoryId={}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            ArrayNode arr = MAPPER.createArrayNode();
            for (ChatMessage m : messages) {
                ObjectNode node = toNode(m);
                if (node != null) {
                    arr.add(node);
                }
            }
            RedisUtils.set(key(memoryId), MAPPER.writeValueAsString(arr), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存AI会话上下文失败 memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        RedisUtils.del(key(memoryId));
    }

    private String key(Object memoryId) {
        return RedisKey.getKey(RedisKey.AI_CONTEXT_STRING, memoryId);
    }

    private ObjectNode toNode(ChatMessage m) {
        ObjectNode node = MAPPER.createObjectNode();
        if (m instanceof SystemMessage sm) {
            node.put("type", "system");
            node.put("text", sm.text());
        } else if (m instanceof UserMessage um) {
            node.put("type", "user");
            node.put("text", um.hasSingleText() ? stripRetrievedMemory(um.singleText()) : "");
        } else if (m instanceof AiMessage am) {
            node.put("type", "ai");
            node.put("text", am.text());
            if (am.hasToolExecutionRequests()) {
                ArrayNode reqs = MAPPER.createArrayNode();
                for (ToolExecutionRequest req : am.toolExecutionRequests()) {
                    ObjectNode r = MAPPER.createObjectNode();
                    r.put("id", req.id());
                    r.put("name", req.name());
                    r.put("arguments", req.arguments());
                    reqs.add(r);
                }
                node.set("toolExecutionRequests", reqs);
            }
        } else if (m instanceof ToolExecutionResultMessage trm) {
            node.put("type", "toolResult");
            node.put("id", trm.id());
            node.put("toolName", trm.toolName());
            node.put("text", trm.text());
        } else {
            return null;
        }
        return node;
    }

    private String stripRetrievedMemory(String text) {
        int marker = text == null ? -1 : text.indexOf("[[USER_MESSAGE]]");
        return marker < 0 ? text : text.substring(marker + "[[USER_MESSAGE]]".length());
    }

    private ChatMessage fromNode(JsonNode node) {
        String type = node.path("type").asText();
        switch (type) {
            case "system":
                return new SystemMessage(node.path("text").asText(""));
            case "user":
                return new UserMessage(node.path("text").asText(""));
            case "ai": {
                String text = node.path("text").asText(null);
                JsonNode reqsNode = node.path("toolExecutionRequests");
                if (reqsNode.isArray() && reqsNode.size() > 0) {
                    List<ToolExecutionRequest> reqs = new ArrayList<>();
                    for (JsonNode r : reqsNode) {
                        reqs.add(ToolExecutionRequest.builder()
                                .id(r.path("id").asText())
                                .name(r.path("name").asText())
                                .arguments(r.path("arguments").asText())
                                .build());
                    }
                    return new AiMessage(text, reqs);
                }
                return new AiMessage(text == null ? "" : text);
            }
            case "toolResult":
                return new ToolExecutionResultMessage(
                        node.path("id").asText(), node.path("toolName").asText(), node.path("text").asText(""));
            default:
                return null;
        }
    }
}
