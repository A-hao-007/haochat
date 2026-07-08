package com.ahao.haochat.common.chatai.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具基类：提供取参与 JSON Schema 构造的公共方法，避免各工具重复样板。
 */
public abstract class AbstractMcpTool implements McpTool {

    protected String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    protected Long longVal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected Map<String, Object> strProp(String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", desc);
        return p;
    }

    /** 构造 object 类型的 JSON Schema */
    protected Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        List<String> req = new ArrayList<>();
        for (String r : required) {
            req.add(r);
        }
        schema.put("required", req);
        return schema;
    }
}
