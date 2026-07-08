package com.ahao.haochat.common.chatai.mcp.tool;

import cn.hutool.core.date.DateUtil;
import com.ahao.haochat.common.chatai.agent.task.AgentTask;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskDao;
import com.ahao.haochat.common.chatai.agent.task.AgentTaskStatus;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创建定时提醒：写入 agent_task 表（task_type=REMINDER），由 AgentTaskScheduler 到点投递。
 */
@Slf4j
@Component
public class CreateReminderTool extends AbstractMcpTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AgentTaskDao agentTaskDao;

    @Override
    public String name() {
        return "createReminder";
    }

    @Override
    public String description() {
        return "创建一个定时提醒，到点后机器人会在当前会话提醒你。时间需为 yyyy-MM-dd HH:mm 格式";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("time", strProp("提醒时间，格式 yyyy-MM-dd HH:mm"));
        props.put("content", strProp("提醒内容"));
        return objectSchema(props, "time", "content");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long callerUid = longVal(args.get("_callerUid"));
        Long roomId = longVal(args.get("_roomId"));
        String timeStr = str(args.get("time"));
        String content = str(args.get("content"));
        if (callerUid == null || roomId == null) {
            return "缺少会话上下文，无法创建提醒";
        }
        if (timeStr.isEmpty() || content.isEmpty()) {
            return "请提供提醒时间与内容";
        }
        Date remindAt;
        try {
            remindAt = DateUtil.parse(timeStr);
        } catch (Exception e) {
            return "时间格式无法识别，请使用 yyyy-MM-dd HH:mm";
        }
        if (remindAt.getTime() <= System.currentTimeMillis()) {
            return "提醒时间必须晚于当前时间";
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content", content);
            Date now = new Date();
            AgentTask task = AgentTask.builder()
                    .uid(callerUid)
                    .roomId(roomId)
                    .taskType("REMINDER")
                    .status(AgentTaskStatus.PENDING)
                    .payload(MAPPER.writeValueAsString(payload))
                    .nextRunTime(remindAt)
                    .runCount(0)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            agentTaskDao.save(task);
        } catch (Exception e) {
            log.warn("createReminder error", e);
            return "提醒创建失败: " + e.getMessage();
        }
        return "已创建提醒，将在 " + DateUtil.formatDateTime(remindAt) + " 提醒你：" + content;
    }
}
