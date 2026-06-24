package com.ahao.haochat.common.chatai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haochat.deepseek")
public class DeepSeekProperties {
    /** 是否启用 */
    private boolean use = true;
    /** AI 用户 ID */
    private Long uid = 10001L;
    /** API Key（在 https://platform.deepseek.com 获取） */
    private String key = "";
    /** API 地址 */
    private String url = "https://api.deepseek.com/v1/chat/completions";
    /** 模型名称 */
    private String model = "deepseek-chat";
    /** 超时秒数 */
    private int timeout = 60;
    /** 最大 token */
    private int maxTokens = 2048;
    /** 每小时限制 */
    private int limit = 30;
}
