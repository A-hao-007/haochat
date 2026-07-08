package com.ahao.haochat.common.user.domain.vo.request.auth;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/** 邮箱登录：邮箱 + 密码 */
@Data
public class EmailLoginReq {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式有误")
    @ApiModelProperty("邮箱")
    private String email;

    @NotBlank(message = "请输入密码")
    @ApiModelProperty("密码")
    private String password;
}
