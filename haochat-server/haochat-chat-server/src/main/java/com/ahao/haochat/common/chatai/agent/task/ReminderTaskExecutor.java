package com.ahao.haochat.common.chatai.agent.task;

import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.ChatMessageReq;
import com.ahao.haochat.common.chat.domain.vo.request.msg.TextMsgReq;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chatai.properties.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 一次性提醒任务：到点由机器人在对应会话发一条提醒消息，然后置为已完成。
 * 取代旧版 CreateReminderTool 直接写 Redis ZSet 的方式。
 */
@Slf4j
@Component
public class ReminderTaskExecutor implements TaskExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ChatService chatService;
    @Autowired
    private DeepSeekProperties props;
    @Autowired
    private AgentTaskDao agentTaskDao;

    @Override
    public String taskType() {
        return "REMINDER";
    }

    @Override
    public void execute(AgentTask task) {
        String content;
        try {
            JsonNode node = MAPPER.readTree(task.getPayload());
            content = node.path("content").asText("");
        } catch (Exception e) {
            log.warn("解析提醒任务payload失败 id={}", task.getId(), e);
            content = "";
        }
        ChatMessageReq req = new ChatMessageReq();
        req.setRoomId(task.getRoomId());
        req.setMsgType(MessageTypeEnum.TEXT.getType());
        TextMsgReq body = new TextMsgReq();
        body.setContent("⏰ 提醒：" + content);
        req.setBody(body);
        chatService.sendMsg(req, props.getUid());

        AgentTask update = new AgentTask();
        update.setId(task.getId());
        update.setStatus(AgentTaskStatus.DONE);
        update.setLastRunTime(new Date());
        update.setRunCount((task.getRunCount() == null ? 0 : task.getRunCount()) + 1);
        agentTaskDao.updateById(update);
    }
}
