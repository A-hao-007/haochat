package com.ahao.haochat.common.chatai.handler;

import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.agent.AgentService;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.ahao.haochat.common.user.domain.vo.response.user.UserInfoResp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 助手 —— 使用 DeepSeek 大模型
 * API 地址: https://platform.deepseek.com
 * DeepSeek API 与 OpenAI 格式完全兼容
 */
@Slf4j
@Component
public class DeepSeekChatAIHandler extends AbstractChatAIHandler {

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private AgentService agentService;

    @Override
    protected void init() {
        super.init();
        if (isUse()) {
            UserInfoResp userInfo = userService.getUserInfo(deepSeekProperties.getUid());
            if (userInfo == null || StringUtils.isBlank(userInfo.getName())) {
                log.warn("DeepSeek AI 用户 {} 未找到", deepSeekProperties.getUid());
            }
        }
    }

    @Override
    protected boolean isUse() {
        return deepSeekProperties.isUse() && StringUtils.isNotBlank(deepSeekProperties.getKey());
    }

    @Override
    public Long getChatAIUserId() {
        return deepSeekProperties.getUid();
    }

    @Override
    protected String doChat(Message message) {
        String content = message.getContent() == null ? "" : message.getContent().trim();
        if (StringUtils.isBlank(content)) return "请说点什么吧~";

        try {
            // 走 Agent（function-calling + MCP 工具），流式回复的落库与推送已在 AgentService 内部异步完成，
            // 这里统一返回 null，不需要再走 AbstractChatAIHandler.answerMsg() 发一次
            agentService.run(message);
        } catch (Exception e) {
            log.warn("DeepSeek agent error: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected boolean supports(Message message) {
        return isUse() && StringUtils.isNotBlank(message.getContent());
    }
}
