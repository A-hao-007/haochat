package com.ahao.haochat.common.user.domain.vo.request.auth;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/** 邮箱验证码登录 */
@Data
public class EmailCodeLoginReq {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式有误")
    @ApiModelProperty("邮箱")
    private String email;

    @NotBlank(message = "请输入验证码")
    @ApiModelProperty("邮箱验证码")
    private String code;
}
