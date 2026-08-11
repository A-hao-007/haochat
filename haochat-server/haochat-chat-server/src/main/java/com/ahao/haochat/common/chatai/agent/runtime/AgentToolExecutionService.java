package com.ahao.haochat.common.chatai.agent.runtime;

import com.ahao.haochat.common.chatai.mcp.McpTool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Timeout, bounded retry and circuit breaker for Agent tools. */
@Slf4j
@Service
public class AgentToolExecutionService {

    private static final int CIRCUIT_FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 30_000L;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(8, new ToolThreadFactory());

    @Data
    @AllArgsConstructor
    public static class Result {
        private boolean success;
        private String output;
        private String errorCode;
        private String errorMessage;
        private int attempts;
        private int latencyMs;

        public String userText() {
            return success ? output : "工具执行失败: " + errorMessage;
        }
    }

    public Result executeLocal(McpTool tool, Map<String, Object> args) {
        return execute("local:" + tool.name(), () -> tool.execute(args),
                tool.timeoutSeconds(), tool.maxAttempts());
    }

    public Result executeRemote(String toolName, Callable<String> action) {
        // Remote MCP definitions do not currently expose mutability metadata, so never retry
        // automatically: repeating an unknown remote write could duplicate side effects.
        return execute("remote:" + toolName, action, 12, 1);
    }

    private Result execute(String circuitKey, Callable<String> action, int timeoutSeconds, int maxAttempts) {
        long started = System.currentTimeMillis();
        CircuitState circuit = circuits.computeIfAbsent(circuitKey, ignored -> new CircuitState());
        if (!circuit.allowRequest()) {
            return new Result(false, null, "CIRCUIT_OPEN", "工具暂时熔断，请稍后再试", 0,
                    elapsed(started));
        }

        int safeAttempts = Math.max(1, Math.min(maxAttempts, 3));
        String lastCode = "TOOL_ERROR";
        String lastMessage = "未知错误";
        for (int attempt = 1; attempt <= safeAttempts; attempt++) {
            Future<String> future = executor.submit(action);
            try {
                String output = future.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
                circuit.recordSuccess();
                return new Result(true, output, null, null, attempt, elapsed(started));
            } catch (TimeoutException e) {
                future.cancel(true);
                lastCode = "TOOL_TIMEOUT";
                lastMessage = "执行超过 " + Math.max(1, timeoutSeconds) + " 秒";
                log.warn("Agent工具超时 tool={} attempt={}", circuitKey, attempt);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                lastCode = "TOOL_INTERRUPTED";
                lastMessage = "执行被中断";
                break;
            } catch (Exception e) {
                future.cancel(true);
                Throwable cause = e.getCause() == null ? e : e.getCause();
                lastMessage = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                log.warn("Agent工具失败 tool={} attempt={}", circuitKey, attempt, cause);
            }
            if (attempt < safeAttempts) {
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastCode = "TOOL_INTERRUPTED";
                    lastMessage = "重试等待被中断";
                    break;
                }
            }
        }
        circuit.recordFailure();
        return new Result(false, null, lastCode, lastMessage, safeAttempts, elapsed(started));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private int elapsed(long started) {
        long elapsed = System.currentTimeMillis() - started;
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    private static class CircuitState {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile long openUntil;

        boolean allowRequest() {
            long now = System.currentTimeMillis();
            if (openUntil == 0 || now >= openUntil) {
                if (openUntil != 0) {
                    openUntil = 0;
                    failures.set(0);
                }
                return true;
            }
            return false;
        }

        void recordSuccess() {
            failures.set(0);
            openUntil = 0;
        }

        void recordFailure() {
            if (failures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
                openUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            }
        }
    }

    private static class ToolThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "agent-tool-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
