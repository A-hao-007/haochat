package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.chatai.agent.task.AgentTask;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskDao;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskStatus;
import com.ahao.haochat.common.chatai.agent.task.TaskExecutor;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 任务状态机调度器：取代旧版直接轮询 Redis ZSet 的 AgentScheduler。
 * 按 task_type 分发给对应 {@link TaskExecutor}，具体执行成功/失败后的状态流转由 Executor 自己负责，
 * 这里只负责分发和兜底捕获 Executor 未处理的异常（标记为失败，避免任务卡在执行中反复重试）。
 */
@Slf4j
@Component
public class AgentTaskScheduler {

    @Autowired
    private AgentTaskDao agentTaskDao;
    @Autowired
    private List<TaskExecutor> executorList;
    @Autowired
    private DeepSeekProperties props;

    private Map<String, TaskExecutor> executorMap;

    @PostConstruct
    public void init() {
        executorMap = executorList.stream().collect(Collectors.toMap(TaskExecutor::taskType, e -> e));
    }

    /** 每30秒检查一次到期任务 */
    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
        if (!props.isUse()) {
            return;
        }
        List<AgentTask> dueTasks = agentTaskDao.getDueTasks(new Date());
        for (AgentTask task : dueTasks) {
            TaskExecutor executor = executorMap.get(task.getTaskType());
            if (executor == null) {
                log.warn("未知的Agent任务类型: {}, id={}", task.getTaskType(), task.getId());
                markFailed(task);
                continue;
            }
            try {
                executor.execute(task);
            } catch (Exception e) {
                log.warn("Agent任务执行失败 id={} type={}", task.getId(), task.getTaskType(), e);
                markFailed(task);
            }
        }
    }

    private void markFailed(AgentTask task) {
        AgentTask update = new AgentTask();
        update.setId(task.getId());
        update.setStatus(AgentTaskStatus.FAILED);
        update.setLastRunTime(new Date());
        agentTaskDao.updateById(update);
    }
}
