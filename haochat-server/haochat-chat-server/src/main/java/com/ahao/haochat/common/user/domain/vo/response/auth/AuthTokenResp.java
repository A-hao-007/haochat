package com.ahao.haochat.common.user.domain.vo.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login token response. The legacy token field is kept for old clients and is
 * always the access token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResp {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long refreshExpiresIn;
    private String token;
    private Long uid;
    private String username;
    private String name;
    private String avatar;
}
