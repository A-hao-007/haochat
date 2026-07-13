package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 高风险工具调用的"待确认"状态存取（Redis，TTL 5分钟）。
 * 写入方：{@link McpToolProvider}（工具执行编排层拦截 needsConfirmation()=true 的调用）。
 * 读取方：{@link AgentService}（每次 run() 入口先检查是否有待确认动作，走确认/取消/放弃三分支）。
 */
@Slf4j
@Component
public class PendingConfirmStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TTL_MINUTES = 5;

    public void save(Long roomId, Long uid, PendingConfirmation pending) {
        try {
            RedisUtils.set(key(roomId, uid), MAPPER.writeValueAsString(pending), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存待确认动作失败 roomId={} uid={}", roomId, uid, e);
        }
    }

    public PendingConfirmation load(Long roomId, Long uid) {
        try {
            String json = RedisUtils.getStr(key(roomId, uid));
            if (json == null || json.isEmpty()) {
                return null;
            }
            return MAPPER.readValue(json, PendingConfirmation.class);
        } catch (Exception e) {
            log.warn("加载待确认动作失败 roomId={} uid={}", roomId, uid, e);
            return null;
        }
    }

    public void clear(Long roomId, Long uid) {
        RedisUtils.del(key(roomId, uid));
    }

    private String key(Long roomId, Long uid) {
        return RedisKey.getKey(RedisKey.AI_PENDING_CONFIRM_STRING, roomId, uid);
    }
}
