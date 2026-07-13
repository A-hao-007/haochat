package com.ahao.haochat.common.chatai.agent.task;

import com.ahao.haochat.common.chatai.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
                .in(AgentTask::getStatus, Arrays.asList(AgentTaskStatus.PENDING, AgentTaskStatus.WAITING))
                .le(AgentTask::getNextRunTime, now)
                .list();
    }

    public boolean claimDue(Long taskId, Date now) {
        return lambdaUpdate()
                .eq(AgentTask::getId, taskId)
                .in(AgentTask::getStatus, Arrays.asList(AgentTaskStatus.PENDING, AgentTaskStatus.WAITING))
                .le(AgentTask::getNextRunTime, now)
                .set(AgentTask::getStatus, AgentTaskStatus.RUNNING)
                .set(AgentTask::getLastRunTime, now)
                .update();
    }

    public void markFailed(Long taskId) {
        lambdaUpdate()
                .eq(AgentTask::getId, taskId)
                .set(AgentTask::getStatus, AgentTaskStatus.FAILED)
                .set(AgentTask::getLastRunTime, new Date())
                .update();
    }
}
