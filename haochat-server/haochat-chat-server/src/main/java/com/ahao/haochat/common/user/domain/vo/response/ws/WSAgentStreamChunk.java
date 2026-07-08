package com.ahao.haochat.common.user.domain.vo.response.ws;

import lombok.Data;

/**
 * AI 流式回复片段推送。同一次回复的所有片段共享同一个 streamId，
 * 前端收到首个片段时创建临时消息占位，后续片段原地追加内容，
 * 最终真实消息落库后的正常 MESSAGE 推送到达时替换掉这条临时消息。
 */
@Data
public class WSAgentStreamChunk {
    private Long roomId;
    private String streamId;
    private Long fromUid;
    private String chunk;
}
