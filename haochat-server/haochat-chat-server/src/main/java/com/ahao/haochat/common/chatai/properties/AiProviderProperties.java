package com.ahao.haochat.common.chatai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible model endpoints. DeepSeek, OpenAI and compatible gateways
 * are selected through configuration instead of being coupled to business code.
 */
@Data
@Component
@ConfigurationProperties(prefix = "haochat.ai.provider")
public class AiProviderProperties {

    private Provider primary = new Provider();
    private Provider fallback = new Provider();
    private Provider embedding = new Provider();

    @Data
    public static class Provider {
        private boolean enabled;
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private int timeoutSeconds = 60;
        private int maxRetries = 1;
    }
}
