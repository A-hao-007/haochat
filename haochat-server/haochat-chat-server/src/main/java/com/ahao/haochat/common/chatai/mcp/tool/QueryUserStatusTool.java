package com.ahao.haochat.common.chatai.mcp.tool;

import cn.hutool.core.date.DateUtil;
import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 查询好友在线状态（activeStatus：1 在线 / 2 离线）。
 */
@Component
public class QueryUserStatusTool extends AbstractMcpTool {

    @Autowired
    private UserDao userDao;

    @Override
    public String name() {
        return "queryUserStatus";
    }

    @Override
    public String description() {
        return "查询某个用户当前是否在线及最后活跃时间";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("userId", strProp("目标用户的 uid"));
        return objectSchema(props, "userId");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long userId = longVal(args.get("userId"));
        if (userId == null) {
            return "请提供有效的 userId";
        }
        User user = userDao.getById(userId);
        if (user == null) {
            return "用户不存在";
        }
        boolean online = Objects.equals(user.getActiveStatus(), 1);
        String lastActive = user.getLastOptTime() == null ? "未知" : DateUtil.formatDateTime(user.getLastOptTime());
        return String.format("用户「%s」(uid=%d) 当前%s，最后活跃时间：%s",
                user.getName(), userId, online ? "在线" : "离线", lastActive);
    }
}
