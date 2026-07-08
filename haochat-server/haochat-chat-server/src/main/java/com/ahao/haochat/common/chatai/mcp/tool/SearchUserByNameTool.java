package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.domain.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按昵称/用户名模糊搜索用户，返回匹配到的 uid 列表。
 * 用于兜底"当前会话成员映射表里查不到"的情况——模型提到的人不在群里/不是当前对话方时，
 * 先搜索而不是直接放弃。
 */
@Component
public class SearchUserByNameTool extends AbstractMcpTool {

    @Autowired
    private UserDao userDao;

    @Override
    public String name() {
        return "searchUserByName";
    }

    @Override
    public String description() {
        return "按昵称或用户名模糊搜索用户，返回匹配到的 uid 列表。"
                + "当提到的人不在当前会话成员列表里时，优先用这个工具查找，而不是直接说不知道";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", strProp("要搜索的昵称或用户名关键字"));
        return objectSchema(props, "keyword");
    }

    @Override
    public String execute(Map<String, Object> args) {
        String keyword = str(args.get("keyword"));
        if (keyword.isEmpty()) {
            return "请提供搜索关键字";
        }
        List<User> list = userDao.searchByKeyword(keyword, null);
        if (list.isEmpty()) {
            return "没有找到昵称/用户名包含「" + keyword + "」的用户";
        }
        StringBuilder sb = new StringBuilder("找到以下用户：\n");
        for (User u : list) {
            sb.append("- ").append(u.getName()).append("(uid=").append(u.getId()).append(")\n");
        }
        return sb.toString().trim();
    }
}
