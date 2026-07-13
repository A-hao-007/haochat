package com.ahao.haochat.common.user.service;

import cn.hutool.core.util.StrUtil;
import com.ahao.haochat.common.common.constant.RedisKey;
import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.common.utils.RedisUtils;
import com.ahao.haochat.common.common.utils.RequestHolder;
import com.ahao.haochat.common.user.dao.UserLoginLogDao;
import com.ahao.haochat.common.user.domain.entity.IpDetail;
import com.ahao.haochat.common.user.domain.entity.UserLoginLog;
import com.ahao.haochat.common.user.service.impl.IpServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthSecurityService {
    private static final int MAX_FAIL_COUNT = 5;
    private static final int FAIL_WINDOW_MINUTES = 15;

    @Resource
    private UserLoginLogDao userLoginLogDao;

    public void checkLoginAllowed(String principal) {
        String key = failKey(principal);
        try {
            String failCount = RedisUtils.getStr(key);
            if (StrUtil.isNotBlank(failCount) && Integer.parseInt(failCount) >= MAX_FAIL_COUNT) {
                throw new BusinessException("登录失败次数过多，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("check login fail count degraded, key={}", key, e);
        }
    }

    public void onLoginFailure(Long uid, String principal, String loginType, String reason) {
        try {
            RedisUtils.integerInc(failKey(principal), FAIL_WINDOW_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("record login fail count degraded, principal={}", principal, e);
        }
        recordLogin(uid, principal, loginType, false, reason);
    }

    public void onLoginSuccess(Long uid, String principal, String loginType) {
        try {
            RedisUtils.del(failKey(principal));
        } catch (Exception e) {
            log.warn("clear login fail count degraded, principal={}", principal, e);
        }
        recordLogin(uid, principal, loginType, true, null);
    }

    public void recordLogin(Long uid, String principal, String loginType, boolean success, String failReason) {
        recordLogin(uid, principal, loginType, success, failReason, getIp(), getUserAgent());
    }

    public void recordLogin(Long uid, String principal, String loginType, boolean success, String failReason, String ip, String userAgent) {
        try {
            String deviceId = deviceId(userAgent, ip);
            String ipRegion = resolveIpRegion(ip);
            userLoginLogDao.save(UserLoginLog.builder()
                    .uid(uid)
                    .principal(principal)
                    .loginType(loginType)
                    .success(success ? 1 : 0)
                    .failReason(failReason)
                    .ip(ip)
                    .ipRegion(ipRegion)
                    .deviceId(deviceId)
                    .deviceName(deviceName(userAgent))
                    .userAgent(userAgent)
                    .createTime(new Date())
                    .build());
        } catch (Exception e) {
            log.warn("record login log failed, uid={}, principal={}", uid, principal, e);
        }
    }

    public String getIp() {
        return RequestHolder.get() == null ? null : RequestHolder.get().getIp();
    }

    public String getUserAgent() {
        return RequestHolder.get() == null ? null : RequestHolder.get().getUserAgent();
    }

    public String deviceId(String userAgent, String ip) {
        return sha256(StrUtil.blankToDefault(userAgent, "unknown") + "|" + StrUtil.blankToDefault(ip, "unknown"));
    }

    public String deviceName(String userAgent) {
        if (StrUtil.isBlank(userAgent)) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone") || ua.contains("ipad")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os")) {
            return "macOS";
        }
        return "Browser";
    }

    public String resolveIpRegion(String ip) {
        if (StrUtil.isBlank(ip)) {
            return null;
        }
        try {
            IpDetail detail = IpServiceImpl.getIpDetailOrNull(ip);
            if (detail == null) {
                return null;
            }
            return String.join(" ",
                    StrUtil.blankToDefault(detail.getCountry(), ""),
                    StrUtil.blankToDefault(detail.getRegion(), ""),
                    StrUtil.blankToDefault(detail.getCity(), "")).trim();
        } catch (Exception e) {
            log.warn("resolve ip region failed, ip={}", ip, e);
            return null;
        }
    }

    private String failKey(String principal) {
        String ip = StrUtil.blankToDefault(getIp(), "unknown");
        return RedisKey.getKey(RedisKey.LOGIN_FAIL_STRING, ip,
                StrUtil.blankToDefault(principal, "unknown").toLowerCase(Locale.ROOT));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
