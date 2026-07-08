package com.ahao.haochat.common.chatai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent system prompt 配置化，支持 application-prod.properties 覆盖，不用改代码重新编译部署。
 */
@Data
@Component
@ConfigurationProperties(prefix = "haochat.ai")
public class AiPromptProperties {

    /** 为空则使用 AgentService 内置的默认文案 */
    private String systemPrompt = "";
}
