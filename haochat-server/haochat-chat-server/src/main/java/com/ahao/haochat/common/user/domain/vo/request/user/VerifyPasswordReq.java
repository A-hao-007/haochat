package com.ahao.haochat.common.user.domain.vo.request.user;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 修改密码前验证当前密码。 */
@Data
public class VerifyPasswordReq {
    @NotBlank(message = "请输入当前密码")
    @ApiModelProperty("当前密码")
    private String password;
}
