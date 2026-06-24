package com.ahao.haochat.common.chatai.handler;

import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.domain.ChatGPTMsg;
import com.ahao.haochat.common.chatai.properties.ChatGPTProperties;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.ahao.haochat.common.chatai.utils.ChatGPTUtils;
import com.ahao.haochat.common.user.domain.vo.response.user.UserInfoResp;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 内置智能 AI 助手 —— 无需 API Key 即可工作
 * 当 OpenAI Key 未配置时自动使用此实现
 */
@Slf4j
@Component
public class BuiltinChatAIHandler extends AbstractChatAIHandler {

    @Autowired
    private ChatGPTProperties chatGPTProperties;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Override
    protected void init() {
        super.init();
        if (isUse()) {
            UserInfoResp userInfo = userService.getUserInfo(chatGPTProperties.getAIUserId());
            if (userInfo == null || StringUtils.isBlank(userInfo.getName())) {
                log.error("AI user {} not found, creating fallback", chatGPTProperties.getAIUserId());
            }
        }
    }

    @Override
    protected boolean isUse() {
        // 仅当 DeepSeek 和 ChatGPT 都未配置时激活
        boolean deepseekOn = deepSeekProperties.isUse() && StringUtils.isNotBlank(deepSeekProperties.getKey());
        boolean gptOn = chatGPTProperties.isUse() && StringUtils.isNotBlank(chatGPTProperties.getKey())
                && !chatGPTProperties.getKey().startsWith("sk-xxx");
        return !deepseekOn && !gptOn;
    }

    @Override
    public Long getChatAIUserId() {
        return chatGPTProperties.getAIUserId();
    }

    @Override
    protected String doChat(Message message) {
        String content = message.getContent().trim();
        if (StringUtils.isBlank(content)) return "请说点什么吧~";

        // 尝试用真实 OpenAI API（如果配置了有效 Key）
        String gptReply = tryOpenAI(message, content);
        if (gptReply != null) return gptReply;

        // 内置智能回复
        return builtinReply(content);
    }

    /**
     * 尝试调用 OpenAI API，失败返回 null
     */
    private String tryOpenAI(Message message, String content) {
        String key = chatGPTProperties.getKey();
        if (StringUtils.isBlank(key) || key.startsWith("sk-xxx") || key.startsWith("YOUR_")) {
            return null;
        }
        try {
            ChatGPTMsg userMsg = new ChatGPTMsg();
            userMsg.setRole("user");
            userMsg.setContent(content);
            Response response = ChatGPTUtils.create(key)
                    .proxyUrl(chatGPTProperties.getProxyUrl())
                    .model(chatGPTProperties.getModelName())
                    .timeout(chatGPTProperties.getTimeout())
                    .maxTokens(chatGPTProperties.getMaxTokens())
                    .message(Collections.singletonList(userMsg))
                    .send();
            return ChatGPTUtils.parseText(response);
        } catch (Exception e) {
            log.warn("OpenAI call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 内置智能回复引擎
     */
    private String builtinReply(String msg) {
        String lower = msg.toLowerCase().trim();

        // 问候
        if (matches(lower, "hi", "hello", "你好", "嗨", "在吗", "在不在")) {
            return "你好呀！我是 HaoChat AI 助手 🤖\n有什么可以帮助你的吗？";
        }

        // 自我介绍
        if (lower.contains("你是谁") || lower.contains("你叫什么") || lower.contains("你的名字")) {
            return "我是 HaoChat 智能助手！\n"
                    + "我可以陪你聊天、回答问题、提供建议。\n"
                    + "💡 提示：管理员配置 OpenAI API Key 后我就能变得更聪明哦～";
        }

        // 天气
        if (lower.contains("天气")) {
            return "我暂时还不能查询实时天气 😅\n你可以打开手机天气 App 看看～";
        }

        // 时间
        if (lower.contains("几点") || lower.contains("时间") || lower.contains("日期")) {
            return "现在是 " + java.time.LocalDateTime.now().toString().replace("T", " ") + "\n抓紧时间做有意义的事吧！💪";
        }

        // 笑话
        if (lower.contains("笑话") || lower.contains("搞笑") || lower.contains("开心")) {
            String[] jokes = {
                "为什么程序员不喜欢游泳？因为水里全是 Bug！🐛",
                "从前有个程序员，他写了一个程序，后来他改了又改，又改了，又改了…",
                "世界上最好的语言是什么？是女朋友说的话 💕",
            };
            return jokes[(int) (Math.random() * jokes.length)];
        }

        // 感谢
        if (matches(lower, "谢谢", "感谢", "多谢", "thanks", "thank")) {
            return "不客气！随时为你服务 😊";
        }

        // 再见
        if (matches(lower, "拜拜", "再见", "bye", "88", "晚安")) {
            return "再见！祝你生活愉快～🌙";
        }

        // 帮助
        if (lower.contains("帮助") || lower.contains("功能") || lower.contains("能做什么")) {
            return "我能做这些事：\n"
                    + "💬 陪你聊天解闷\n"
                    + "😄 讲笑话给你听\n"
                    + "⏰ 告诉你现在时间\n"
                    + "📝 回答简单问题\n"
                    + "━━━━━━━━━━━━\n"
                    + "💡 配置 OpenAI Key 后可以：\n"
                    + "   • 智能对话\n"
                    + "   • 代码编写\n"
                    + "   • 翻译\n"
                    + "   • 知识问答";
        }

        // 默认回复
        String[] defaults = {
            "有意思～继续说 😄",
            "嗯嗯，我在听 👂",
            "这个问题有点难度，让我想想… 🤔",
            "你可以试试问我：时间、笑话、帮助",
            "哈哈，有意思！",
            "💡 提示：发送「帮助」看看我能做什么",
        };
        return defaults[(int) (Math.random() * defaults.length)];
    }

    private boolean matches(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.equals(kw)) return true;
        }
        return false;
    }

    @Override
    protected boolean supports(Message message) {
        return StringUtils.isNotBlank(message.getContent());
    }
}
