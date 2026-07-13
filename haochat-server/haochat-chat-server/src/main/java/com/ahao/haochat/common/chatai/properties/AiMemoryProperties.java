package com.ahao.haochat.common.chatai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for optional long-term AI memory services. */
@Data
@Component
@ConfigurationProperties(prefix = "haochat.ai.memory")
public class AiMemoryProperties {
    private boolean enabled;
    private boolean queryRewriteEnabled = true;
    private String embeddingModel = "text-embedding-3-small";
    private int topK = 6;
    private String qdrantUrl = "http://qdrant:6333";
    private String qdrantCollection = "haochat_memory";
    private String neo4jUrl = "http://neo4j:7474";
    private String neo4jUsername = "neo4j";
    private String neo4jPassword = "";
}
