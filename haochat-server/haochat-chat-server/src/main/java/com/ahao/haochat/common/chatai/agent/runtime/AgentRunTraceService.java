package com.ahao.haochat.common.chatai.agent.runtime;

import com.ahao.haochat.common.chatai.mapper.AgentRunMapper;
import com.ahao.haochat.common.chatai.mapper.AgentRunStepMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Durable Agent run/step trace. Trace failures are deliberately isolated from chat delivery,
 * while request-id uniqueness still prevents the same message from starting two Agent runs.
 */
@Slf4j
@Service
public class AgentRunTraceService extends ServiceImpl<AgentRunMapper, AgentRun> {

    private final AgentRunStepMapper stepMapper;

    public AgentRunTraceService(AgentRunStepMapper stepMapper) {
        this.stepMapper = stepMapper;
    }

    @Data
    @AllArgsConstructor
    public static class StartResult {
        private String runId;
        private boolean created;
    }

    public StartResult start(String requestId, Long uid, Long roomId, String input,
                             String provider, String model) {
        String runId = UUID.randomUUID().toString();
        Date now = new Date();
        AgentRun run = AgentRun.builder()
                .runId(runId)
                .requestId(requestId)
                .uid(uid)
                .roomId(roomId)
                .status(AgentRunStatus.RUNNING)
                .currentStep("REQUEST_RECEIVED")
                .provider(truncate(provider, 64))
                .model(truncate(model, 128))
                .userInput(truncate(input, 2048))
                .toolCallCount(0)
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            save(run);
            return new StartResult(runId, true);
        } catch (DuplicateKeyException duplicate) {
            AgentRun existing = lambdaQuery().eq(AgentRun::getRequestId, requestId).one();
            return new StartResult(existing == null ? runId : existing.getRunId(), false);
        } catch (Exception e) {
            try {
                AgentRun existing = lambdaQuery().eq(AgentRun::getRequestId, requestId).one();
                if (existing != null) {
                    return new StartResult(existing.getRunId(), false);
                }
            } catch (Exception ignored) {
                // Fall through to degraded tracing below.
            }
            // Observability must not make the assistant unavailable. A synthetic run id keeps
            // in-process tool correlation working even when the trace database is degraded.
            log.warn("创建Agent Run失败 requestId={}", requestId, e);
            return new StartResult(runId, true);
        }
    }

    public String beginStep(String runId, String type, String name, String inputSummary) {
        if (runId == null) return null;
        String stepId = UUID.randomUUID().toString();
        try {
            AgentRunStep step = AgentRunStep.builder()
                    .stepId(stepId)
                    .runId(runId)
                    .stepType(type)
                    .stepName(truncate(name, 128))
                    .status(AgentStepStatus.RUNNING)
                    .inputSummary(truncate(inputSummary, 2000))
                    .attemptCount(1)
                    .createTime(new Date())
                    .build();
            stepMapper.insert(step);
            lambdaUpdate().eq(AgentRun::getRunId, runId)
                    .set(AgentRun::getCurrentStep, truncate(name, 128))
                    .set(AgentRun::getUpdateTime, new Date())
                    .update();
        } catch (Exception e) {
            log.warn("记录Agent步骤开始失败 runId={} step={}", runId, name, e);
        }
        return stepId;
    }

    public void finishStep(String stepId, String status, String outputSummary,
                           int attempts, int latencyMs, String errorCode, String errorMessage) {
        if (stepId == null) return;
        try {
            AgentRunStep update = new AgentRunStep();
            update.setStatus(status);
            update.setOutputSummary(truncate(outputSummary, 4000));
            update.setAttemptCount(Math.max(1, attempts));
            update.setLatencyMs(Math.max(0, latencyMs));
            update.setErrorCode(truncate(errorCode, 64));
            update.setErrorMessage(truncate(errorMessage, 1000));
            update.setFinishTime(new Date());
            stepMapper.update(update,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentRunStep>()
                            .eq(AgentRunStep::getStepId, stepId));
        } catch (Exception e) {
            log.warn("记录Agent步骤完成失败 stepId={}", stepId, e);
        }
    }

    public void markFirstToken(String runId, int latencyMs) {
        if (runId == null) return;
        try {
            lambdaUpdate().eq(AgentRun::getRunId, runId)
                    .isNull(AgentRun::getFirstTokenLatencyMs)
                    .set(AgentRun::getFirstTokenLatencyMs, Math.max(0, latencyMs))
                    .set(AgentRun::getUpdateTime, new Date())
                    .update();
        } catch (Exception e) {
            log.warn("更新Agent首字延迟失败 runId={}", runId, e);
        }
    }

    public void incrementToolCalls(String runId) {
        if (runId == null) return;
        try {
            lambdaUpdate().eq(AgentRun::getRunId, runId)
                    .setSql("tool_call_count = tool_call_count + 1")
                    .set(AgentRun::getUpdateTime, new Date())
                    .update();
        } catch (Exception e) {
            log.warn("更新Agent工具次数失败 runId={}", runId, e);
        }
    }

    public void waitingConfirmation(String runId, String reply, int latencyMs) {
        finishRun(runId, AgentRunStatus.WAITING_CONFIRMATION, "WAITING_CONFIRMATION",
                reply, latencyMs, null, null, false);
    }

    public void succeed(String runId, String reply, int latencyMs) {
        finishRun(runId, AgentRunStatus.SUCCEEDED, "COMPLETED",
                reply, latencyMs, null, null, true);
    }

    public void fail(String runId, String reply, int latencyMs, String errorCode, String errorMessage) {
        finishRun(runId, AgentRunStatus.FAILED, "FAILED",
                reply, latencyMs, errorCode, errorMessage, true);
    }

    public void cancel(String runId, String reply, int latencyMs) {
        finishRun(runId, AgentRunStatus.CANCELLED, "CANCELLED",
                reply, latencyMs, null, null, true);
    }

    private void finishRun(String runId, String status, String currentStep, String reply,
                           int latencyMs, String errorCode, String errorMessage, boolean terminal) {
        if (runId == null) return;
        try {
            int totalLatency = elapsedSinceCreate(runId, latencyMs);
            var update = lambdaUpdate().eq(AgentRun::getRunId, runId)
                    .set(AgentRun::getStatus, status)
                    .set(AgentRun::getCurrentStep, currentStep)
                    .set(AgentRun::getFinalReply, truncate(reply, 8000))
                    .set(AgentRun::getLatencyMs, totalLatency)
                    .set(AgentRun::getErrorCode, truncate(errorCode, 64))
                    .set(AgentRun::getErrorMessage, truncate(errorMessage, 1000))
                    .set(AgentRun::getUpdateTime, new Date());
            if (terminal) {
                update.set(AgentRun::getFinishTime, new Date());
            }
            update.update();
        } catch (Exception e) {
            log.warn("更新Agent Run状态失败 runId={} status={}", runId, status, e);
        }
    }

    private int elapsedSinceCreate(String runId, int fallback) {
        try {
            AgentRun run = lambdaQuery().select(AgentRun::getCreateTime)
                    .eq(AgentRun::getRunId, runId).one();
            if (run != null && run.getCreateTime() != null) {
                long elapsed = System.currentTimeMillis() - run.getCreateTime().getTime();
                return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, elapsed);
            }
        } catch (Exception ignored) {
        }
        return Math.max(0, fallback);
    }

    public List<AgentRun> recent(Long uid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return lambdaQuery().eq(AgentRun::getUid, uid)
                .orderByDesc(AgentRun::getId).last("limit " + safeLimit).list();
    }

    public Map<String, Object> detail(Long uid, String runId) {
        AgentRun run = lambdaQuery().eq(AgentRun::getRunId, runId)
                .eq(AgentRun::getUid, uid).one();
        if (run == null) return Map.of();
        List<AgentRunStep> steps = stepMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentRunStep>()
                        .eq(AgentRunStep::getRunId, runId)
                        .orderByAsc(AgentRunStep::getId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run);
        result.put("steps", steps);
        return result;
    }

    public Map<String, Object> metrics(Long uid, int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        Date since = new Date(System.currentTimeMillis() - safeDays * 86_400_000L);
        List<AgentRun> runs = lambdaQuery().eq(AgentRun::getUid, uid)
                .ge(AgentRun::getCreateTime, since)
                .orderByDesc(AgentRun::getId).last("limit 2000").list();
        long succeeded = runs.stream().filter(r -> AgentRunStatus.SUCCEEDED.equals(r.getStatus())).count();
        long failed = runs.stream().filter(r -> AgentRunStatus.FAILED.equals(r.getStatus())).count();
        long waiting = runs.stream().filter(r -> AgentRunStatus.WAITING_CONFIRMATION.equals(r.getStatus())).count();
        long cancelled = runs.stream().filter(r -> AgentRunStatus.CANCELLED.equals(r.getStatus())).count();
        List<Integer> latencies = runs.stream().map(AgentRun::getLatencyMs).filter(java.util.Objects::nonNull)
                .sorted().collect(Collectors.toList());
        List<Integer> firstTokens = runs.stream().map(AgentRun::getFirstTokenLatencyMs)
                .filter(java.util.Objects::nonNull).sorted().collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowDays", safeDays);
        result.put("total", runs.size());
        result.put("succeeded", succeeded);
        result.put("failed", failed);
        result.put("waitingConfirmation", waiting);
        result.put("cancelled", cancelled);
        result.put("successRate", runs.isEmpty() ? 0D : succeeded * 1D / runs.size());
        result.put("avgLatencyMs", average(latencies));
        result.put("p95LatencyMs", percentile95(latencies));
        result.put("avgFirstTokenLatencyMs", average(firstTokens));
        result.put("p95FirstTokenLatencyMs", percentile95(firstTokens));
        result.put("totalToolCalls", runs.stream().map(AgentRun::getToolCallCount)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum());
        return result;
    }

    private int average(List<Integer> values) {
        return values.isEmpty() ? 0 : (int) values.stream().mapToInt(Integer::intValue).average().orElse(0D);
    }

    private int percentile95(List<Integer> sorted) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(sorted.size() * 0.95D) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
