package com.ahao.haochat.common.common.factory;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带计数的丢弃策略。丢弃本身可以接受（推送有客户端拉取兜底），
 * 不可接受的是 DiscardPolicy 静默丢弃——"用户偶尔收不到推送"完全无法排查。
 * 日志按时间间隔采样，避免过载时自身刷屏加剧问题。
 */
@Slf4j
public class CountingDiscardPolicy implements RejectedExecutionHandler {
    private static final long LOG_INTERVAL_MS = 5000;
    private final String poolName;
    private final AtomicLong discardCount = new AtomicLong();
    private volatile long lastLogTime = 0L;

    public CountingDiscardPolicy(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        long total = discardCount.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= LOG_INTERVAL_MS) {
            lastLogTime = now;
            log.warn("线程池[{}]队列已满，任务被丢弃，累计丢弃 {} 个（队列 {}/活跃线程 {}）",
                    poolName, total, executor.getQueue().size(), executor.getActiveCount());
        }
    }

    public long getDiscardCount() {
        return discardCount.get();
    }
}
