package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chat.domain.entity.RoomFriend;
import com.ahao.haochat.common.chat.domain.enums.MessageTypeEnum;
import com.ahao.haochat.common.chat.domain.vo.request.ChatMessageReq;
import com.ahao.haochat.common.chat.domain.vo.request.msg.TextMsgReq;
import com.ahao.haochat.common.chat.service.ChatService;
import com.ahao.haochat.common.chat.service.RoomService;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代发消息：以调用者身份，向「调用者与目标用户」的单聊会话发送一条文本消息。
 */
@Component
public class SendMessageTool extends AbstractMcpTool {

    @Autowired
    private RoomService roomService;
    @Autowired
    private ChatService chatService;

    @Override
    public String name() {
        return "sendMessage";
    }

    @Override
    public String description() {
        return "代当前用户向某位好友发送一条文本消息";
    }

    @Override
    public boolean needsConfirmation() {
        // 代发消息是不可撤销的写操作，必须先让用户确认内容再真正发送
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("userId", strProp("接收方好友的 uid"));
        props.put("content", strProp("要发送的文本内容"));
        return objectSchema(props, "userId", "content");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long callerUid = longVal(args.get("_callerUid"));
        Long targetUid = longVal(args.get("userId"));
        String content = str(args.get("content"));
        if (callerUid == null || targetUid == null || content.isEmpty()) {
            return "参数不完整，需要 userId 与 content";
        }
        RoomFriend room = roomService.getFriendRoom(callerUid, targetUid);
        if (room == null) {
            return "你和该用户不是好友，无法发送消息";
        }
        ChatMessageReq req = new ChatMessageReq();
        req.setRoomId(room.getRoomId());
        req.setMsgType(MessageTypeEnum.TEXT.getType());
        TextMsgReq body = new TextMsgReq();
        body.setContent(content);
        req.setBody(body);
        chatService.sendMsg(req, callerUid);
        return "已发送给 uid=" + targetUid;
    }
}
