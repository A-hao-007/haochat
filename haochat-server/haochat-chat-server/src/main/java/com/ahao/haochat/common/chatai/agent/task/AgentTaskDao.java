package com.ahao.haochat.common.chatai.agent.task;

import com.ahao.haochat.common.chatai.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Agent 任务状态机 服务实现类
 * </p>
 */
@Service
public class AgentTaskDao extends ServiceImpl<AgentTaskMapper, AgentTask> {

    /** 待执行/等待事件 且 到达调度时间的任务 */
    public List<AgentTask> getDueTasks(Date now) {
        return lambdaQuery()
                .and(wrapper -> wrapper
                        .in(AgentTask::getStatus, AgentTaskStatus.PENDING, AgentTaskStatus.WAITING)
                        .or(stale -> stale.eq(AgentTask::getStatus, AgentTaskStatus.RUNNING)
                                .isNotNull(AgentTask::getLeaseUntil)
                                .le(AgentTask::getLeaseUntil, now)))
                .le(AgentTask::getNextRunTime, now)
                .last("limit 100")
                .list();
    }

    public boolean claimDue(Long taskId, Date now, String leaseToken) {
        Date leaseUntil = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(2));
        return baseMapper.claimDue(taskId, now, leaseUntil, leaseToken) == 1;
    }

    public boolean markDone(Long taskId, String leaseToken) {
        return baseMapper.markDone(taskId, leaseToken) == 1;
    }

    public void markFailed(Long taskId, String leaseToken, Throwable error) {
        AgentTask task = getById(taskId);
        if (task == null || leaseToken == null || !leaseToken.equals(task.getLeaseToken())) {
            return;
        }
        int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        boolean exhausted = retryCount >= 3;
        long delaySeconds = 30L * (1L << Math.min(retryCount - 1, 4));
        String message = error == null ? "没有对应的任务执行器" : error.getMessage();
        if (message != null && message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        lambdaUpdate()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING)
                .eq(AgentTask::getLeaseToken, leaseToken)
                .set(AgentTask::getStatus, exhausted ? AgentTaskStatus.FAILED : AgentTaskStatus.WAITING)
                .set(AgentTask::getLastRunTime, new Date())
                .set(AgentTask::getNextRunTime,
                        exhausted ? task.getNextRunTime() : new Date(System.currentTimeMillis() + delaySeconds * 1000L))
                .set(AgentTask::getRetryCount, retryCount)
                .set(AgentTask::getLastError, message)
                .set(AgentTask::getLeaseUntil, null)
                .set(AgentTask::getLeaseToken, null)
                .update();
    }
}
