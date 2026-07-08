package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chat.dao.MessageDao;
import com.ahao.haochat.common.chat.domain.entity.Message;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在当前会话内按关键字搜索历史文本消息。
 */
@Component
public class SearchHistoryTool extends AbstractMcpTool {

    private static final int LIMIT = 10;

    @Autowired
    private MessageDao messageDao;

    @Override
    public String name() {
        return "searchHistory";
    }

    @Override
    public String description() {
        return "在当前聊天会话中按关键字搜索历史消息";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", strProp("要搜索的关键字"));
        return objectSchema(props, "keyword");
    }

    @Override
    public String execute(Map<String, Object> args) {
        String keyword = str(args.get("keyword"));
        Long roomId = longVal(args.get("_roomId"));
        if (keyword.isEmpty()) {
            return "请提供搜索关键字";
        }
        if (roomId == null) {
            return "缺少会话上下文，无法搜索";
        }
        List<Message> list = messageDao.searchByKeyword(roomId, keyword, LIMIT);
        if (list.isEmpty()) {
            return "未找到包含「" + keyword + "」的历史消息";
        }
        StringBuilder sb = new StringBuilder("找到 ").append(list.size()).append(" 条相关消息：\n");
        for (Message m : list) {
            sb.append("- ").append(m.getContent()).append('\n');
        }
        return sb.toString().trim();
    }
}
