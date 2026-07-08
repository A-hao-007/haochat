package com.ahao.haochat.common.chatai.mcp.tool;

import com.ahao.haochat.common.chatai.mcp.AbstractMcpTool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 查询城市天气：调用公共服务 wttr.in，无需 API Key。
 */
@Slf4j
@Component
public class GetWeatherTool extends AbstractMcpTool {

    @Override
    public String name() {
        return "getWeather";
    }

    @Override
    public String description() {
        return "查询指定城市的实时天气";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("city", strProp("城市名称，例如 北京"));
        return objectSchema(props, "city");
    }

    @Override
    public String execute(Map<String, Object> args) {
        String city = str(args.get("city"));
        if (city.isEmpty()) {
            return "请提供城市名称";
        }
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build();
            String url = "https://wttr.in/" + URLEncoder.encode(city, StandardCharsets.UTF_8.name()) + "?format=3";
            Request request = new Request.Builder().url(url).header("User-Agent", "curl/8.0").build();
            try (Response resp = client.newCall(request).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String text = resp.body().string().trim();
                    return text.isEmpty() ? "未查询到该城市天气" : text;
                }
                return "天气查询失败";
            }
        } catch (Exception e) {
            log.warn("getWeather error city={}", city, e);
            return "天气查询失败: " + e.getMessage();
        }
    }
}
