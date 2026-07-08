package com.ahao.haochat.common.common.utils;

import cn.hutool.extra.servlet.ServletUtil;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * IP 工具类 — 统一获取客户端真实 IP
 * <p>
 * 优先级：X-Forwarded-For（逗号分隔取第一个）→ X-Real-IP → RemoteAddr
 * </p>
 *
 * @author haochat
 */
public class IpUtils {

    /**
     * 从 HttpServletRequest 获取客户端真实 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "未知";
        }
        // 1. 优先从 X-Forwarded-For 获取（Caddy 默认传递，逗号分隔取第一个）
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor) && !"unknown".equalsIgnoreCase(forwardedFor)) {
            // 多级代理时格式为 "client, proxy1, proxy2"，取第一个
            int idx = forwardedFor.indexOf(',');
            return idx > 0 ? forwardedFor.substring(0, idx).trim() : forwardedFor.trim();
        }
        // 2. 其次从 X-Real-IP 获取
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        // 3. 兜底：直接远端地址（生产环境会是代理容器 IP）
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.isNotBlank(remoteAddr) ? remoteAddr : "未知";
    }

    /**
     * 从 Netty HTTP headers 获取客户端真实 IP（用于 WebSocket 握手阶段）
     *
     * @param xForwardedFor X-Forwarded-For header 值（可能为 null）
     * @param xRealIp       X-Real-IP header 值（可能为 null）
     * @param remoteAddr    远端 TCP 地址（兜底）
     * @return 真实 IP 字符串
     */
    public static String getClientIpFromHeaders(String xForwardedFor, String xRealIp, String remoteAddr) {
        // 1. X-Forwarded-For（逗号分隔取第一个）
        if (StringUtils.isNotBlank(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            int idx = xForwardedFor.indexOf(',');
            return idx > 0 ? xForwardedFor.substring(0, idx).trim() : xForwardedFor.trim();
        }
        // 2. X-Real-IP
        if (StringUtils.isNotBlank(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }
        // 3. RemoteAddr 兜底
        return StringUtils.isNotBlank(remoteAddr) ? remoteAddr : "未知";
    }
}
