package com.ahao.haochat.common.user.service;

import com.ahao.haochat.common.user.domain.vo.response.auth.AuthTokenResp;

public interface LoginService {
    boolean verify(String token);

    void renewalTokenIfNecessary(String token);

    String login(Long uid);

    AuthTokenResp loginWithTokens(Long uid);

    AuthTokenResp loginWithTokens(Long uid, String ip, String userAgent);

    AuthTokenResp refresh(String refreshToken);

    void logout(String accessToken, String refreshToken);

    Long getValidUid(String token);
}
