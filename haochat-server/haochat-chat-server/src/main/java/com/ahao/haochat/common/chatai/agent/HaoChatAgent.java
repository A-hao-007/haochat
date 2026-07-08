package com.ahao.haochat.common.chatai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices 代理接口。memoryId 约定为 "roomId:uid"，
 * 与 {@link RedisChatMemoryStore} 的 Redis key 规则、{@link McpToolProvider} 的隐藏参数注入规则保持一致。
 * 返回 {@link TokenStream}（流式）而不是 String：注册 onPartialResponse/onCompleteResponse/onError
 * 等回调后调用 .start() 才会真正发起请求，回调可能运行在非调用方线程上。
 */
public interface HaoChatAgent {

    TokenStream chat(@MemoryId String memoryId, @UserMessage String userText);
}
