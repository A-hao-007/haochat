package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chat.service.RoomAppService;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布群公告（需要调用者是群主/管理员，权限校验由 RoomAppServiceImpl.updateNotice 内部完成）。
 */
@Component
public class CreateGroupNoticeTool extends AbstractMcpTool {

    @Autowired
    private RoomAppService roomAppService;

    @Override
    public String name() {
        return "createGroupNotice";
    }

    @Override
    public String description() {
        return "在当前群聊发布一条群公告，仅群主/管理员可用";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("content", strProp("群公告内容"));
        return objectSchema(props, "content");
    }

    @Override
    public boolean needsConfirmation() {
        // 群公告对全群成员可见，必须先让用户确认文案再真正发布
        return true;
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long callerUid = longVal(args.get("_callerUid"));
        Long roomId = longVal(args.get("_roomId"));
        String content = str(args.get("content"));
        if (callerUid == null || roomId == null) {
            return "缺少会话上下文，无法发布公告";
        }
        if (content.isEmpty()) {
            return "请提供公告内容";
        }
        try {
            roomAppService.updateNotice(callerUid, roomId, content);
        } catch (Exception e) {
            return "发布失败: " + e.getMessage();
        }
        return "群公告已发布：" + content;
    }
}
