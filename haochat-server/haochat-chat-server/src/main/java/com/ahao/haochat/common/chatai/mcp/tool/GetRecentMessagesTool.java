package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import com.ahao.haochat.common.common.domain.vo.request.CursorPageBaseReq;
import com.ahao.haochat.common.common.domain.vo.response.CursorPageBaseResp;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.service.cache.UserInfoCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 取当前会话最近N条消息（不需要关键字），供"总结一下这个群最近在聊什么"这类任务使用。
 * 总结能力本身不做成后端逻辑，由模型拿到原始消息后自行归纳。
 */
@Component
public class GetRecentMessagesTool extends AbstractMcpTool {

    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 50;

    @Autowired
    private MessageDao messageDao;
    @Autowired
    private UserInfoCache userInfoCache;

    @Override
    public String name() {
        return "getRecentMessages";
    }

    @Override
    public String description() {
        return "获取当前会话最近的消息记录（不需要关键字），用于总结聊天内容等场景";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("count", strProp("要获取的消息条数，默认20，最多50"));
        return objectSchema(props);
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long roomId = longVal(args.get("_roomId"));
        if (roomId == null) {
            return "缺少会话上下文，无法获取消息";
        }
        Long countVal = longVal(args.get("count"));
        int count = countVal == null ? DEFAULT_COUNT : Math.min(Math.max(countVal.intValue(), 1), MAX_COUNT);

        CursorPageBaseReq req = new CursorPageBaseReq(count, null);
        CursorPageBaseResp<Message> page = messageDao.getCursorPage(roomId, req, null);
        List<Message> list = page.getList();
        if (list == null || list.isEmpty()) {
            return "当前会话暂无消息";
        }

        List<Long> uidList = list.stream().map(Message::getFromUid).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userInfoCache.getBatch(uidList);

        // getCursorPage 按id倒序返回（最新在前），转成时间正序更符合"聊天记录"的直觉
        List<Message> ordered = new ArrayList<>(list);
        Collections.reverse(ordered);

        StringBuilder sb = new StringBuilder("最近 ").append(ordered.size()).append(" 条消息：\n");
        for (Message m : ordered) {
            User user = userMap.get(m.getFromUid());
            String name = user != null && user.getName() != null ? user.getName() : ("用户" + m.getFromUid());
            String content = StringUtils.isBlank(m.getContent()) ? "[非文本消息]" : m.getContent();
            sb.append(name).append(": ").append(content).append('\n');
        }
        return sb.toString().trim();
    }
}
