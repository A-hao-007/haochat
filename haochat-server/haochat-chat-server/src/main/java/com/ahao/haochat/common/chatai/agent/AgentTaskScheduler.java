package com.ahao.haochat.common.chatai.agent;

import com.ahao.haochat.common.chatai.agent.task.AgentTask;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskDao;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.ahao.haochat.common.common.constant.MQEventType;
import com.ahao.haochat.common.common.service.BusinessEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Scans due tasks and publishes retryable delayed events to RocketMQ. */
@Slf4j
@Component
public class AgentTaskScheduler {
    @Autowired
    private AgentTaskDao agentTaskDao;
    @Autowired
    private DeepSeekProperties props;
    @Autowired
    private BusinessEventPublisher businessEventPublisher;

    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
        if (!props.isUse()) {
            return;
        }
        List<AgentTask> dueTasks = agentTaskDao.getDueTasks(new Date());
        for (AgentTask task : dueTasks) {
            try {
                businessEventPublisher.publish(MQEventType.REMINDER_DUE, String.valueOf(task.getId()), task.getUid(),
                        Collections.singletonMap("taskId", task.getId()));
            } catch (Exception e) {
                // Keep the task due so the next scheduler tick can retry the MQ send.
                log.warn("failed to publish reminder task id={}", task.getId(), e);
            }
        }
    }
}
