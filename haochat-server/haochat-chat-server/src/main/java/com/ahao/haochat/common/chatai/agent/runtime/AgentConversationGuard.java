package com.ahao.haochat.common.chatai.agent.runtime;

import com.ahao.haochat.common.common.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** Distributed single-flight guard for one user in one conversation. */
@Component
@RequiredArgsConstructor
public class AgentConversationGuard {

    private static final Duration TTL = Duration.ofMinutes(3);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean acquire(Long roomId, Long uid, String token) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key(roomId, uid), token, TTL);
        return Boolean.TRUE.equals(created);
    }

    public void release(Long roomId, Long uid, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key(roomId, uid)), token);
    }

    private String key(Long roomId, Long uid) {
        return RedisKey.join("agent", "conversation", "active", roomId, uid);
    }
}
