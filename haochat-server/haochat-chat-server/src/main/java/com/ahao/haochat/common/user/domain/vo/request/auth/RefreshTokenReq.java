package com.ahao.haochat.common.user.domain.vo.request.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RefreshTokenReq {
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
