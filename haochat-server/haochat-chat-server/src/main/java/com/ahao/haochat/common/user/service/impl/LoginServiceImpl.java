package com.ahao.haochat.common.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ahao.haochat.common.common.exception.BusinessException;
import com.ahao.haochat.common.common.utils.JwtUtils;
import com.ahao.haochat.common.user.dao.UserDao;
import com.ahao.haochat.common.user.dao.UserSessionDao;
import com.ahao.haochat.common.user.domain.entity.User;
import com.ahao.haochat.common.user.domain.entity.UserSession;
import com.ahao.haochat.common.user.domain.vo.response.auth.AuthTokenResp;
import com.ahao.haochat.common.user.service.AuthSecurityService;
import com.ahao.haochat.common.user.service.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LoginServiceImpl implements LoginService {
    private static final long ACCESS_EXPIRE_MINUTES = 15;
    private static final long REFRESH_EXPIRE_DAYS = 30;

    @Resource
    private JwtUtils jwtUtils;
    @Resource
    private UserSessionDao userSessionDao;
    @Resource
    private UserDao userDao;
    @Resource
    private AuthSecurityService authSecurityService;

    @Override
    public boolean verify(String token) {
        Long uid = jwtUtils.getUidOrNull(token);
        if (uid == null) {
            return false;
        }
        String type = jwtUtils.getTokenTypeOrNull(token);
        if (!JwtUtils.TYPE_ACCESS.equals(type)) {
            return false;
        }
        Long sessionId = jwtUtils.getSessionIdOrNull(token);
        if (sessionId == null) {
            return false;
        }
        UserSession session = userSessionDao.getActiveSession(sessionId);
        return session != null && Objects.equals(session.getUid(), uid);
    }

    @Override
    public void renewalTokenIfNecessary(String token) {
        // Access tokens are intentionally short lived. Use refreshToken instead.
    }

    @Override
    public String login(Long uid) {
        return loginWithTokens(uid).getAccessToken();
    }

    @Override
    public AuthTokenResp loginWithTokens(Long uid) {
        return loginWithTokens(uid, authSecurityService.getIp(), authSecurityService.getUserAgent());
    }

    @Override
    public AuthTokenResp loginWithTokens(Long uid, String ip, String userAgent) {
        User user = userDao.getById(uid);
        Date now = new Date();
        Date refreshExpireTime = new Date(now.getTime() + TimeUnit.DAYS.toMillis(REFRESH_EXPIRE_DAYS));
        UserSession session = UserSession.builder()
                .uid(uid)
                .deviceId(authSecurityService.deviceId(userAgent, ip))
                .deviceName(authSecurityService.deviceName(userAgent))
                .userAgent(userAgent)
                .ip(ip)
                .ipRegion(authSecurityService.resolveIpRegion(ip))
                .status(0)
                .expireTime(refreshExpireTime)
                .lastActiveTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        userSessionDao.save(session);

        String accessToken = jwtUtils.createAccessToken(uid, session.getId(), ACCESS_EXPIRE_MINUTES);
        String refreshToken = jwtUtils.createRefreshToken(uid, session.getId(), UUID.randomUUID().toString().replace("-", ""), REFRESH_EXPIRE_DAYS);
        updateRefreshTokenHash(session.getId(), refreshToken, refreshExpireTime);
        return buildResp(user, accessToken, refreshToken);
    }

    @Override
    public AuthTokenResp refresh(String refreshToken) {
        Long uid = jwtUtils.getUidOrNull(refreshToken);
        Long sessionId = jwtUtils.getSessionIdOrNull(refreshToken);
        String type = jwtUtils.getTokenTypeOrNull(refreshToken);
        if (uid == null || sessionId == null || !JwtUtils.TYPE_REFRESH.equals(type)) {
            throw new BusinessException("refreshToken无效");
        }
        UserSession session = userSessionDao.getActiveSession(sessionId);
        if (session == null || !Objects.equals(session.getUid(), uid)
                || !Objects.equals(session.getRefreshTokenHash(), sha256(refreshToken))) {
            throw new BusinessException("refreshToken无效");
        }
        User user = userDao.getById(uid);
        Date refreshExpireTime = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(REFRESH_EXPIRE_DAYS));
        String nextRefreshToken = jwtUtils.createRefreshToken(uid, sessionId, UUID.randomUUID().toString().replace("-", ""), REFRESH_EXPIRE_DAYS);
        String accessToken = jwtUtils.createAccessToken(uid, sessionId, ACCESS_EXPIRE_MINUTES);
        updateRefreshTokenHash(sessionId, nextRefreshToken, refreshExpireTime);
        return buildResp(user, accessToken, nextRefreshToken);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        Long sessionId = null;
        if (StrUtil.isNotBlank(refreshToken)) {
            sessionId = jwtUtils.getSessionIdOrNull(refreshToken);
        }
        if (sessionId == null && StrUtil.isNotBlank(accessToken)) {
            sessionId = jwtUtils.getSessionIdOrNull(accessToken);
        }
        userSessionDao.revoke(sessionId);
    }

    @Override
    public Long getValidUid(String token) {
        return verify(token) ? jwtUtils.getUidOrNull(token) : null;
    }

    private AuthTokenResp buildResp(User user, String accessToken, String refreshToken) {
        return AuthTokenResp.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(TimeUnit.MINUTES.toSeconds(ACCESS_EXPIRE_MINUTES))
                .refreshExpiresIn(TimeUnit.DAYS.toSeconds(REFRESH_EXPIRE_DAYS))
                .token(accessToken)
                .uid(user == null ? null : user.getId())
                .username(user == null ? null : user.getUsername())
                .name(user == null ? null : user.getName())
                .avatar(user == null ? null : user.getAvatar())
                .build();
    }

    private void updateRefreshTokenHash(Long sessionId, String refreshToken, Date expireTime) {
        UserSession update = new UserSession();
        update.setId(sessionId);
        update.setRefreshTokenHash(sha256(refreshToken));
        update.setExpireTime(expireTime);
        update.setLastActiveTime(new Date());
        update.setUpdateTime(new Date());
        userSessionDao.updateById(update);
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
