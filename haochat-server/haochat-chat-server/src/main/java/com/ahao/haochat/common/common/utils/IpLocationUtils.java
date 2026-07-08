package com.ahao.haochat.common.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * 离线IP属地解析（ip2region，整库加载进内存，纯内存查询无IO，线程安全，无外部网络调用）。
 *
 * @author haochat
 */
@Slf4j
public class IpLocationUtils {

    private static final String UNKNOWN = "未知";
    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.|0:0:0:0:0:0:0:1|::1)");
    // ip2region 对部分移动数据/数据中心IP段精度不足，会把运营商名字直接填进"城市"这一槎位，需要过滤掉避免误展示成地址
    private static final Pattern ISP_KEYWORD = Pattern.compile(
            "电信|联通|移动|铁通|广电|教育网|长城宽带|方正宽带|聚友宽带|有线通|华数|阿里云|腾讯云|华为云|亚马逊|AWS|Azure|谷歌云",
            Pattern.CASE_INSENSITIVE);

    private static final Searcher SEARCHER = loadSearcher();

    private IpLocationUtils() {
    }

    private static Searcher loadSearcher() {
        try (InputStream is = IpLocationUtils.class.getResourceAsStream("/ip2region/ip2region.xdb")) {
            if (is == null) {
                log.warn("ip2region.xdb 资源未找到，IP属地解析将始终返回'未知'");
                return null;
            }
            byte[] bytes = is.readAllBytes();
            LongByteArray buffer = new LongByteArray();
            buffer.append(bytes);
            log.info("ip2region 离线IP库加载完成，大小={}KB", bytes.length / 1024);
            return Searcher.newWithBuffer(Version.IPv4, buffer);
        } catch (Exception e) {
            log.error("ip2region 离线IP库加载失败", e);
            return null;
        }
    }

    /**
     * 解析IP属地，只返回城市（如 "杭州"）。失败/内网/未知IP返回"未知"。
     * 注意：ip2region 原始数据格式为 "国家|区域|省份|城市|ISP"，这里只取城市，不展示运营商信息。
     */
    public static String resolve(String ip) {
        if (SEARCHER == null || StringUtils.isBlank(ip) || UNKNOWN.equals(ip) || PRIVATE_IP.matcher(ip).find()) {
            return UNKNOWN;
        }
        try {
            String region = SEARCHER.search(ip);
            return format(region);
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private static String format(String region) {
        if (StringUtils.isBlank(region)) {
            return UNKNOWN;
        }
        String[] parts = region.split("\\|");
        String province = clean(parts.length > 2 ? parts[2] : null);
        String city = clean(parts.length > 3 ? parts[3] : null);
        // 只展示城市；部分IP（如数据中心/专线）没有城市粒度，退化展示省份
        if (city != null) {
            return city;
        }
        return province != null ? province : UNKNOWN;
    }

    private static String clean(String s) {
        if (StringUtils.isBlank(s) || "0".equals(s) || "内网IP".equals(s)) {
            return null;
        }
        // 部分IP段（移动数据/数据中心）ip2region的城市/省份槎位里实际填的是运营商或云厂商名字，过滤掉避免误展示
        if (ISP_KEYWORD.matcher(s).find()) {
            return null;
        }
        return s;
    }
}
