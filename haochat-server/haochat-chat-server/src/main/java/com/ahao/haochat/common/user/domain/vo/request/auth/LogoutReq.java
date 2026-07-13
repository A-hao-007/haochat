package com.ahao.haochat.common.user.domain.vo.request.auth;

import lombok.Data;

@Data
public class LogoutReq {
    private String refreshToken;
}
